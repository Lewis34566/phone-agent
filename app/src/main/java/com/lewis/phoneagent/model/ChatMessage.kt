package com.lewis.phoneagent.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 聊天消息：UI 展示 + Room 持久化共用同一个模型。
 *
 * contentType 决定气泡渲染方式：
 *  TEXT/IMAGE    —— 文本/图片气泡
 *  TOOL_RESULT   —— 工具结果卡片
 *  CONFIRM       —— 确认卡片
 *  content 对非文本类型存 JSON（或图片路径），渲染时解析。
 *  sessionId：会话分组（0 = 默认会话），新开会话/历史记录依赖它。
 */
@Entity(tableName = "history")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long = 0,
    val role: Role,
    val contentType: ContentType,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class Role { USER, ASSISTANT }

enum class ContentType { TEXT, IMAGE, TOOL_RESULT, CONFIRM }
