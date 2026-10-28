package com.lewis.phoneagent.model

import kotlinx.serialization.Serializable

/**
 * 确认卡片的载荷（CONFIRM 类型消息的 content 序列化成它）。
 * actionId 由 Dispatcher 生成，用于用户点「确认/取消」后找回待执行的操作。
 * 重启 App 后内存中的待执行操作丢失，点按钮会得到"操作已失效"的诚实提示。
 */
@Serializable
data class ConfirmPayload(
    val actionId: String,
    val title: String,
    val description: String,
)
