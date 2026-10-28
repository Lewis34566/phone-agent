package com.lewis.phoneagent.core.llm

import android.content.Context
import android.util.Base64
import com.lewis.phoneagent.data.secure.KeystoreCrypto
import com.lewis.phoneagent.util.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import java.util.concurrent.TimeUnit

/**
 * 文生图统一接口 —— 与 LlmProvider 同构的独立模块：业务只依赖接口，服务商可换。
 *
 * 诚实边界：
 *  - 生图必须云端（本地设备跑不动大模型），需用户在设置页配置生图服务的 key；
 *  - 生视频接口未实现（成本高、国内服务少），后续版本按同一模式加 VideoGenProvider。
 */
interface ImageGenProvider {
    /** 生成一张图，返回图片字节；失败抛异常由调用方转提示 */
    suspend fun generate(prompt: String, size: String): ByteArray
}

/** 生图服务配置（独立于对话模型配置；apiKey 同样 Keystore 加密存储） */
class ImageGenConfigStore(context: Context) {

    private val prefs = context.getSharedPreferences("image_gen_config", Context.MODE_PRIVATE)
    private val crypto = KeystoreCrypto()

    data class Config(val baseUrl: String, val apiKey: String, val model: String) {
        val isReady get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
    }

    fun load(): Config {
        val key = prefs.getString(K_API_KEY, null)?.let { crypto.decrypt(it) } ?: ""
        return Config(
            baseUrl = prefs.getString(K_BASE_URL, "") ?: "",
            apiKey = key,
            model = prefs.getString(K_MODEL, "") ?: "",
        )
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

/** OpenAI 兼容生图实现（硅基流动/通义/智谱等 images/generations 接口通用） */
class OpenAiCompatImageProvider(
    private val store: ImageGenConfigStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // 生图慢，读超时放宽
        .build(),
) : ImageGenProvider {

    override suspend fun generate(prompt: String, size: String): ByteArray = withContext(Dispatchers.IO) {
        val config = store.load()
        if (!config.isReady) {
            throw IllegalStateException("生图服务未配置：去设置页「生图服务」填写 baseUrl / apiKey / model")
        }
        val body = buildJsonObject {
            put("model", config.model)
            put("prompt", prompt)
            put("n", 1)
            put("size", size)
        }
        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/images/generations")
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("生图服务返回 HTTP ${response.code}，检查配置")
            }
            val obj = AppJson.json.parseToJsonElement(
                response.body?.string() ?: throw IllegalStateException("生图服务空响应"),
            ).jsonObject
            val data = obj["data"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw IllegalStateException("生图响应格式异常")
            // 优先 b64_json，其次 url（下载）
            data["b64_json"]?.jsonPrimitive?.contentOrNull?.let { b64 ->
                return@withContext Base64.decode(b64, Base64.DEFAULT)
            }
            val url = data["url"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalStateException("生图响应既无图片数据也无 URL")
            val imgResp = client.newCall(Request.Builder().url(url).build()).execute()
            if (!imgResp.isSuccessful) throw IllegalStateException("下载生成图失败：HTTP ${imgResp.code}")
            imgResp.body?.bytes() ?: throw IllegalStateException("下载生成图失败：空响应")
        }
    }
}
