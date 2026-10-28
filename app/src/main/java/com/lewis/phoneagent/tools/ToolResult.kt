package com.lewis.phoneagent.tools

import com.lewis.phoneagent.model.AppInfo
import kotlinx.serialization.Serializable

/**
 * 工具执行结果 —— UI 层根据 sealed 类型渲染对应卡片。
 * 每个类型都带 note 字段：用于标注数据口径、权限边界等诚实说明。
 */
sealed interface ToolResult {

    /** 纯文本结果 */
    data class Text(val text: String) : ToolResult

    /** 应用列表（列表卡片） */
    data class AppList(
        val title: String,
        val apps: List<AppInfo>,
        val note: String? = null,
    ) : ToolResult

    /** 排行类结果（如：长期未用排行、后台活动排行） */
    data class Ranking(
        val title: String,
        val rows: List<RankRow>,
        val note: String? = null,
    ) : ToolResult

    /** 某类权限未授权：UI 渲染引导卡片（带"去授权"按钮跳系统设置），不崩溃不静默失败 */
    data class NeedPermission(
        val permission: RequiredPermission,
        val message: String,
    ) : ToolResult

    /** 图片结果（生图工具），path 为本地文件路径 */
    data class Image(
        val title: String,
        val imagePath: String,
        val note: String? = null,
    ) : ToolResult

    /** 小游戏结果：UI 渲染「启动游戏」按钮 */
    data class Game(val title: String, val note: String? = null) : ToolResult
}

/** 排行中的一行：主文案 + 副文案（如 应用名 + 最后使用时间） */
data class RankRow(
    val title: String,
    val subtitle: String,
    val packageName: String? = null,
)

/** 需要用户去系统设置授权的权限类型 */
enum class RequiredPermission {
    /** 「使用情况访问」权限：僵尸应用检测、占用空间、后台活动排行都依赖它 */
    USAGE_ACCESS,
}

// ---------------------------------------------------------------------------
// 序列化 DTO：ToolResult 存 Room / 跨层传递时转成 JSON。
// 手动 DTO 而非 kotlinx sealed 多态：零多态配置风险，字段平铺直观。
// ---------------------------------------------------------------------------

@Serializable
data class ToolResultDto(
    val type: String,               // "text" | "app_list" | "ranking" | "need_permission" | "image"
    val text: String? = null,
    val title: String? = null,
    val note: String? = null,
    val apps: List<AppInfo> = emptyList(),
    val rows: List<RankRowDto> = emptyList(),
    val permission: String? = null,
    val message: String? = null,
    val imagePath: String? = null,
)

@Serializable
data class RankRowDto(
    val title: String,
    val subtitle: String,
    val packageName: String? = null,
)

/** ToolResult → 可序列化 DTO（存 Room 前调用） */
fun ToolResult.toDto(): ToolResultDto = when (this) {
    is ToolResult.Text -> ToolResultDto(type = "text", text = text)
    is ToolResult.AppList -> ToolResultDto(
        type = "app_list", title = title, note = note, apps = apps,
    )
    is ToolResult.Ranking -> ToolResultDto(
        type = "ranking", title = title, note = note,
        rows = rows.map { RankRowDto(it.title, it.subtitle, it.packageName) },
    )
    is ToolResult.NeedPermission -> ToolResultDto(
        type = "need_permission", permission = permission.name, message = message,
    )
    is ToolResult.Image -> ToolResultDto(
        type = "image", title = title, note = note, imagePath = imagePath,
    )
    is ToolResult.Game -> ToolResultDto(
        type = "game", title = title, note = note,
    )
}

/** DTO → ToolResult（UI 渲染前调用） */
fun ToolResultDto.toToolResult(): ToolResult = when (type) {
    "text" -> ToolResult.Text(text ?: "")
    "app_list" -> ToolResult.AppList(title ?: "应用列表", apps, note)
    "ranking" -> ToolResult.Ranking(
        title ?: "排行",
        rows.map { RankRow(it.title, it.subtitle, it.packageName) },
        note,
    )
    "need_permission" -> ToolResult.NeedPermission(
        permission = permission?.let { p -> runCatching { RequiredPermission.valueOf(p) }.getOrNull() }
            ?: RequiredPermission.USAGE_ACCESS,
        message = message ?: "",
    )
    "image" -> ToolResult.Image(title ?: "图片", imagePath ?: "", note)
    "game" -> ToolResult.Game(title ?: "小游戏", note)
    else -> ToolResult.Text(text ?: "")
}
