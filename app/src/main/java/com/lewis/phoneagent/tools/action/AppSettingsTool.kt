package com.lewis.phoneagent.tools.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.lewis.phoneagent.core.intent.AppNameResolver
import com.lewis.phoneagent.core.intent.RuleMatcher
import com.lewis.phoneagent.tools.MatchScore
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolArgs
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.tools.ToolSpec

/**
 * 操作工具③：跳转到指定应用的系统设置页（清缓存、强制停止、权限管理都在那里）。
 *
 * 诚实边界（核心）：
 *  - 普通 App 无权直接清理其他应用的缓存/强制停止，只能跳转系统页面由用户手动操作，
 *    这是 Android 的安全设计，界面上明确说明。
 *  - 自动清缓存/自动强制停止需要 Shizuku/无障碍，属于 PrivilegedTool 预留扩展，不实现。
 */
class AppSettingsTool(
    private val context: Context,
    private val resolver: AppNameResolver,
) : Tool {

    override val spec = ToolSpec(
        name = "app_settings",
        description = "跳转到指定应用的系统设置页（清缓存、强制停止、权限管理）",
    )

    override fun match(text: String): MatchScore? {
        // 必须带"设置/缓存/存储/强制停止"等词，且能抽出应用名
        val hasSettingsIntent = text.contains("设置") || text.contains("缓存")
            || text.contains("存储") || text.contains("强制停止") || text.contains("详情")
        if (!hasSettingsIntent) return null
        val name = RuleMatcher.extractActionTarget(
            text,
            verbs = listOf("打开", "查看", "看", "看看", "去"),
        )
        if (name == null) {
            // 只说了"设置"没带应用名 → 低分命中，执行时给引导
            return MatchScore(0.55f, emptyMap())
        }
        // 比"打开X"（0.9）略高，避免和 OpenAppTool 平局抢话
        return MatchScore(0.95f, mapOf("appName" to name))
    }

    override suspend fun execute(args: ToolArgs): ToolResult {
        val name = args["appName"] as? String
        if (name.isNullOrBlank()) {
            return ToolResult.Text("想看哪个应用的设置？试试说「打开微信的设置」。")
        }

        val candidates = resolver.resolve(name)
        if (candidates.isEmpty()) {
            return ToolResult.Text("没找到叫「$name」的应用，换个准确的名字试试？")
        }
        if (candidates.size > 1) {
            return ToolResult.Text(
                "「$name」可能指：" + candidates.take(5).joinToString("、") { it.label } +
                    "。请说完整的应用名。",
            )
        }

        val app = candidates.first()
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${app.packageName}"),
        )
        return try {
            context.startActivity(intent)
            ToolResult.Text(
                "已打开「${app.label}」的系统设置页。\n" +
                    "在这里可以：清缓存、清数据、强制停止、管理权限。\n" +
                    "说明：普通 App 无权代你执行这些操作（Android 安全设计），所以只能跳转由你手动完成。",
            )
        } catch (e: Exception) {
            ToolResult.Text("跳转失败：${e.message ?: e.javaClass.simpleName}")
        }
    }
}
