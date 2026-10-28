package com.lewis.phoneagent.util

import kotlinx.serialization.json.Json

/** 全局共享的 JSON 实例：消息内容、工具结果 DTO 的序列化/反序列化都用它 */
object AppJson {
    val json = Json {
        ignoreUnknownKeys = true   // 版本演进时旧消息多字段不炸
        encodeDefaults = true      // DTO 默认值也写入，反序列化字段完整
    }
}
