package com.lewis.phoneagent.ui.chat

import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.lewis.phoneagent.model.ChatMessage
import com.lewis.phoneagent.model.ConfirmPayload
import com.lewis.phoneagent.model.ContentType
import com.lewis.phoneagent.model.Role
import com.lewis.phoneagent.tools.RequiredPermission
import com.lewis.phoneagent.tools.ToolResultDto
import com.lewis.phoneagent.tools.toToolResult
import com.lewis.phoneagent.util.AppJson

/**
 * 消息气泡：用户右对齐、助手左对齐。
 *  - TEXT：普通文本气泡
 *  - TOOL_RESULT：反序列化 JSON → ResultCard（查询结果/权限引导）
 *  - CONFIRM：反序列化 → ConfirmCard（确认/取消回调到 ViewModel）
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    onConfirm: (messageId: Long, actionId: String) -> Unit = { _, _ -> },
    onCancel: (messageId: Long, actionId: String) -> Unit = { _, _ -> },
    onAppClick: (String) -> Unit = {},
    onLaunchGame: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == Role.USER
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            when (message.contentType) {
                ContentType.TEXT -> {
                    // 文本气泡：长按复制到剪贴板
                    val clipboard = LocalClipboardManager.current
                    val context = LocalContext.current
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .pointerInput(message.id) {
                                detectTapGestures(onLongPress = {
                                    clipboard.setText(AnnotatedString(message.content))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                })
                            },
                    )
                }

                ContentType.IMAGE -> {
                    // 图片消息：content 为本地文件路径，直接解码渲染
                    val bmp = remember(message.content) {
                        runCatching { BitmapFactory.decodeFile(message.content) }.getOrNull()
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "图片",
                            modifier = Modifier
                                .padding(8.dp)
                                .widthIn(max = 260.dp),
                        )
                    } else {
                        Text(
                            "Image failed to load",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }

                ContentType.TOOL_RESULT -> {
                    val context = LocalContext.current
                    val dto = runCatching {
                        AppJson.json.decodeFromString(ToolResultDto.serializer(), message.content)
                    }.getOrNull()
                    if (dto != null) {
                        ResultCard(
                            result = dto.toToolResult(),
                            onGrantPermission = { permission ->
                                when (permission) {
                                    RequiredPermission.USAGE_ACCESS ->
                                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                }
                            },
                            onAppClick = onAppClick,
                            onLaunchGame = onLaunchGame,
                            onOpenUrl = onOpenUrl,
                            modifier = Modifier.padding(6.dp),
                        )
                    } else {
                        // 旧版本消息解析失败：诚实降级为原文展示
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }

                ContentType.CONFIRM -> {
                    val payload = runCatching {
                        AppJson.json.decodeFromString(ConfirmPayload.serializer(), message.content)
                    }.getOrNull()
                    if (payload != null) {
                        ConfirmCard(
                            title = payload.title,
                            description = payload.description,
                            onConfirm = { onConfirm(message.id, payload.actionId) },
                            onCancel = { onCancel(message.id, payload.actionId) },
                            modifier = Modifier.padding(6.dp),
                        )
                    } else {
                        Text(
                            text = message.content,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
