package com.lewis.phoneagent.core.llm

import android.content.Context
import android.util.Base64
import com.lewis.phoneagent.data.secure.KeystoreCrypto
import com.lewis.phoneagent.util.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 看图理解（Phone-Vibe 拍照看图能力）：把图片发给多模态模型描述/问答。
 * 独立配置（支持视觉的 OpenAI 兼容服务，如通义 qwen-vl、智谱 glm-4v）。
 * 诚实边界：DeepSeek 不支持图片输入，需要用户配置专门的视觉模型。
 */
interface VisionProvider {
    suspend fun describe(imagePath: String, question: String, onDelta: suspend (String) -> Unit): String
}

class VisionConfigStore(context: Context) {

    private val prefs = context.getSharedPreferences("vision_config", Context.MODE_PRIVATE)
    private val crypto = KeystoreCrypto()

    data class Config(val baseUrl: String, val apiKey: String, val model: String) {
        val isReady get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
    }

    fun load(): Config {
        val key = prefs.getString(K_API_KEY, null)?.let { crypto.decrypt(it) } ?: ""
        return Config(prefs.getString(K_BASE_URL, "") ?: "", key, prefs.getString(K_MODEL, "") ?: "")
    }

    fun save(c: Config) {
        prefs.edit()
            .putString(K_BASE_URL, c.baseUrl.trim())
            .putString(K_API_KEY, crypto.encrypt(c.apiKey.trim()))
            .putString(K_MODEL, c.model.trim())
            .apply()
    }

    fun hasApiKey() = prefs.contains(K_API_KEY)

    private companion object {
        const val K_BASE_URL = "base_url"
        const val K_API_KEY = "api_key"
        const val K_MODEL = "model"
    }
}

class OpenAiCompatVisionProvider(
    private val store: VisionConfigStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build(),
) : VisionProvider {

    override suspend fun describe(imagePath: String, question: String, onDelta: suspend (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            val config = store.load()
            if (!config.isReady) {
                throw IllegalStateException("看图服务未配置：去设置页「看图服务」填 baseUrl / apiKey / model（如通义 qwen-vl-plus）")
            }
            val bytes = File(imagePath).readBytes()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val mime = if (imagePath.endsWith(".png")) "image/png" else "image/jpeg"

            val content = buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", question.ifBlank { "请描述这张图片的内容" })
                })
                add(buildJsonObject {
                    put("type", "image_url")
                    put("image_url", buildJsonObject { put("url", "data:$mime;base64,$b64") })
                })
            }
            val body = buildJsonObject {
                put("model", config.model)
                put("stream", true)
                put("messages", buildJsonArray {
                    add(buildJsonObject { put("role", "user"); put("content", content) })
                })
            }
            val request = Request.Builder()
                .url(config.baseUrl.trimEnd('/') + "/chat/completions")
                .header("Authorization", "Bearer ${config.apiKey}")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("看图服务返回 HTTP ${response.code}")
                val source = response.body?.source() ?: throw IllegalStateException("看图服务空响应")
                val buf = StringBuilder()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    val obj = try { AppJson.json.parseToJsonElement(payload).jsonObject } catch (_: Exception) { continue }
                    val delta = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("delta")?.jsonObject ?: continue
                    delta["content"]?.jsonPrimitive?.contentOrNull?.let { piece ->
                        if (piece.isNotEmpty()) {
                            buf.append(piece)
                            onDelta(piece)
                        }
                    }
                }
                buf.toString().ifBlank { "（看图服务没有返回内容）" }
            }
        }
}
