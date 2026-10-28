package com.lewis.phoneagent.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.lewis.phoneagent.tools.RequiredPermission
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.util.Format

/** 卡片内最多渲染的应用/排行行数（超出折叠，避免几百行卡住聊天列表） */
private const val MAX_ROWS = 30

/**
 * 工具结果卡片：按 ToolResult 的 sealed 类型渲染。
 * note 字段始终展示——数据口径、权限边界、降级说明都在这里诚实交代。
 */
@Composable
fun ResultCard(
    result: ToolResult,
    onGrantPermission: (RequiredPermission) -> Unit,
    onAppClick: (String) -> Unit = {},
    onLaunchGame: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(0.94f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            when (result) {
                is ToolResult.Text -> Text(result.text, style = MaterialTheme.typography.bodyLarge)

                is ToolResult.AppList -> {
                    Text(result.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    result.apps.take(MAX_ROWS).forEach { app ->
                        Row(
                            Modifier
                                .padding(vertical = 4.dp)
                                .clickable { onAppClick(app.label) },
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "v${app.versionName ?: "?"} · ${Format.bytes(app.apkSizeBytes ?: 0)}" +
                                        " · ${Format.date(app.installTimeMs)}" +
                                        if (app.isSystem) " · 系统应用" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                    if (result.apps.size > MAX_ROWS) {
                        Text(
                            "（Showing first $MAX_ROWS of ${result.apps.size}）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }

                is ToolResult.Ranking -> {
                    Text(result.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    result.rows.take(MAX_ROWS).forEach { row ->
                        Row(
                            Modifier
                                .padding(vertical = 4.dp)
                                .clickable {
                                    // 搜索结果行（packageName 存 URL）→ 浏览器打开；否则弹应用操作菜单
                                    if (row.packageName?.startsWith("http") == true) {
                                        onOpenUrl(row.packageName!!)
                                    } else {
                                        onAppClick(row.title)
                                    }
                                },
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    row.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }

                is ToolResult.NeedPermission -> {
                    Text(result.message, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { onGrantPermission(result.permission) }) {
                        Text("Grant access")
                    }
                }

                is ToolResult.Image -> {
                    Text(result.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    val bmp = remember(result.imagePath) {
                        runCatching { android.graphics.BitmapFactory.decodeFile(result.imagePath) }.getOrNull()
                    }
                    if (bmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = result.title,
                            modifier = Modifier.widthIn(max = 260.dp),
                        )
                    } else {
                        Text("图片加载失败", style = MaterialTheme.typography.bodySmall)
                    }
                }

                is ToolResult.Game -> {
                    Text("🎮 ${result.title}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onLaunchGame) { Text("Play game") }
                }
            }

            // note：诚实标注（数据口径、降级说明等），永远展示
            val note = when (result) {
                is ToolResult.AppList -> result.note
                is ToolResult.Ranking -> result.note
                else -> null
            }
            if (!note.isNullOrBlank()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
