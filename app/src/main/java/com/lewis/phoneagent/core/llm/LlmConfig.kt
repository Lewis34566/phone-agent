package com.lewis.phoneagent.core.llm

import android.content.Context
import com.lewis.phoneagent.data.secure.KeystoreCrypto

/**
 * 云端模型配置：baseUrl / apiKey / model。
 * apiKey 用 Keystore 加密后存 SharedPreferences，绝不落明文。
 * 用户在设置页填写；未配置时本地功能不受任何影响（LLM 只是兜底）。
 */
data class LlmConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
) {
    /** 配置是否可用（三项都填才算就绪） */
    val isReady: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

class LlmConfigStore(context: Context) {

    private val prefs = context.getSharedPreferences("llm_config", Context.MODE_PRIVATE)
    private val crypto = KeystoreCrypto()

    fun load(): LlmConfig {
        val apiKey = prefs.getString(KEY_API_KEY, null)
            ?.let { crypto.decrypt(it) }
            ?: ""
        return LlmConfig(
            baseUrl = prefs.getString(KEY_BASE_URL, "") ?: "",
            apiKey = apiKey,
            model = prefs.getString(KEY_MODEL, "") ?: "",
        )
    }

    fun save(config: LlmConfig) {
        prefs.edit()
            .putString(KEY_BASE_URL, config.baseUrl.trim())
            .putString(KEY_API_KEY, crypto.encrypt(config.apiKey.trim()))
            .putString(KEY_MODEL, config.model.trim())
            .apply()
    }

    /** apiKey 是否已设置（设置页据此显示"已填写"，不回显明文） */
    fun hasApiKey(): Boolean = prefs.contains(KEY_API_KEY)

    companion object {
        /** 默认接 DeepSeek（OpenAI 兼容格式，国内直连） */
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"

        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key_encrypted"
        private const val KEY_MODEL = "model"
    }
}
