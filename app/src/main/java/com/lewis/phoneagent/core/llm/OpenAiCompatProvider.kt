package com.lewis.phoneagent.core.llm

import com.lewis.phoneagent.util.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容格式的 LlmProvider 实现（DeepSeek/OpenAI/Kimi/通义等都能接）。
 *
 *  - 流式：SSE 逐行解析，文本增量回调 onDelta；工具调用增量累积 JSON
 *  - 超时：连接 10s / 读 30s（流式期间每次读到数据都会刷新读超时）
 *  - 重试：5xx / 429 / 网络异常重试最多 2 次，指数退避；重试耗尽抛异常由调度层转错误卡片
 *
 * 业务逻辑（Dispatcher/Tools）只依赖 LlmProvider 接口，换任何模型只需新写一个实现类。
 */
class OpenAiCompatProvider(
    private val store: LlmConfigStore,
    private val client: OkHttpClient = defaultClient(),
) : LlmProvider {

    override suspend fun streamChat(
        messages: List<LlmMessage>,
        tools: List<ToolSchema>,
        onDelta: suspend (String) -> Unit,
    ): LlmResult = withContext(Dispatchers.IO) {
        // 每次请求都重新读配置：设置页保存后无需重启立即生效
        val config = store.load()
        if (!config.isReady) throw LlmNotConfiguredException()

        var lastError: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                return@withContext requestOnce(config, messages, tools, onDelta)
            } catch (e: Exception) {
                lastError = e
                // 429/5xx/IO 才重试；4xx（参数错误等）直接失败
                val retryable = e is IOException ||
                    e.message?.contains("429") == true ||
                    e.message?.contains("500") == true || e.message?.contains("502") == true ||
                    e.message?.contains("503") == true || e.message?.contains("504") == true
                if (retryable) {
                    if (attempt < MAX_RETRIES - 1) {
                        Thread.sleep(RETRY_BASE_DELAY_MS shl attempt) // 1s、2s 指数退避
                    }
                } else {
                    throw e
                }
            }
        }
        throw lastError ?: IOException("unknown")
    }

    /** 单次流式请求（同步 OkHttp + 手动 SSE 解析，不引额外依赖） */
    private suspend fun requestOnce(
        config: LlmConfig,
        messages: List<LlmMessage>,
        tools: List<ToolSchema>,
        onDelta: suspend (String) -> Unit,
    ): LlmResult {
        val body = buildJsonObject {
            put("model", config.model)
            put("stream", true)
            put(
                "messages",
                buildJsonArray {
                    messages.forEach { m ->
                        add(buildJsonObject {
                            put("role", m.role)
                            put("content", m.content)
                        })
                    }
                },
            )
            if (tools.isNotEmpty()) {
                put(
                    "tools",
                    buildJsonArray {
                        tools.forEach { t ->
                            add(buildJsonObject {
                                put("type", "function")
                                put(
                                    "function",
                                    buildJsonObject {
                                        put("name", t.name)
                                        put("description", t.description)
                                        put("parameters", AppJson.json.parseToJsonElement(t.parametersJson))
                                    },
                                )
                            })
                        }
                    },
                )
            }
        }

        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val source = response.body?.source()
                ?: throw IOException("空响应体")

            val textBuf = StringBuilder()
            // 多个工具调用按 index 累积（一句话多任务）
            val toolNames = LinkedHashMap<Int, String>()
            val toolArgs = LinkedHashMap<Int, StringBuilder>()
            var finishReason: String? = null

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break

                val obj = try {
                    AppJson.json.parseToJsonElement(payload).jsonObject
                } catch (_: Exception) {
                    continue // 跳过无法解析的行（keep-alive 注释等）
                }
                val choice = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: continue
                val delta = choice["delta"]?.jsonObject ?: continue

                // 文本增量 → 流式渲染
                delta["content"]?.jsonPrimitive?.contentOrNull?.let { piece ->
                    if (piece.isNotEmpty()) {
                        textBuf.append(piece)
                        onDelta(piece)
                    }
                }
                // 工具调用增量 → 按 index 累积名称与参数 JSON
                delta["tool_calls"]?.jsonArray?.forEach { tc ->
                    val idx = tc.jsonObject["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                    val fn = tc.jsonObject["function"]?.jsonObject ?: return@forEach
                    fn["name"]?.jsonPrimitive?.contentOrNull?.let { toolNames[idx] = it }
                    fn["arguments"]?.jsonPrimitive?.contentOrNull?.let {
                        toolArgs.getOrPut(idx) { StringBuilder() }.append(it)
                    }
                }
                choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let { finishReason = it }
            }

            return when {
                finishReason == "tool_calls" && toolNames.isNotEmpty() -> {
                    val calls = toolNames.map { (idx, name) ->
                        LlmResult.ToolCall(name, parseArgs(toolArgs[idx]?.toString() ?: ""))
                    }
                    if (calls.size == 1) calls.first()
                    else LlmResult.MultiToolCall(calls)
                }
                else -> LlmResult.Text(textBuf.toString())
            }
        }
    }

    /** 模型返回的工具参数 JSON → Map；解析失败给空 Map（由调度层校验参数并报错） */
    private fun parseArgs(raw: String): Map<String, Any?> {
        if (raw.isBlank()) return emptyMap()
        return try {
            val obj = AppJson.json.parseToJsonElement(raw).jsonObject
            obj.mapValues { (_, v) ->
                when {
                    v.jsonPrimitive.contentOrNull != null -> v.jsonPrimitive.content
                    else -> v.toString()
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val MAX_RETRIES = 2
        private const val RETRY_BASE_DELAY_MS = 1000L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
