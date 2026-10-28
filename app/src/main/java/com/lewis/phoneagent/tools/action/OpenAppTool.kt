package com.lewis.phoneagent.tools.action

import android.content.Context
import android.content.Intent
import com.lewis.phoneagent.core.intent.AppNameResolver
import com.lewis.phoneagent.core.intent.RuleMatcher
import com.lewis.phoneagent.tools.MatchScore
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolArgs
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.tools.ToolSpec

/**
 * 操作工具①：打开指定应用。
 * 无需任何权限；打开应用无损可逆，不需要确认卡片。
 */
class OpenAppTool(
    private val context: Context,
    private val resolver: AppNameResolver,
) : Tool {

    override val spec = ToolSpec(
        name = "open_app",
        description = "打开（启动）指定应用",
    )

    override fun match(text: String): MatchScore? {
        // "打开X的设置/权限/缓存"这类句式由 AppSettingsTool 处理，这里不抢
        if (text.contains("设置") || text.contains("权限") || text.contains("缓存")
            || text.contains("存储") || text.contains("详情")
        ) return null
        val name = RuleMatcher.extractActionTarget(
            text,
            verbs = listOf("打开", "启动", "运行", "进", "点开", "帮我开"),
        ) ?: return null
        return MatchScore(0.9f, mapOf("appName" to name))
    }

    override suspend fun execute(args: ToolArgs): ToolResult {
        val name = args["appName"] as? String
        if (name.isNullOrBlank()) return ToolResult.Text("想打开哪个应用？试试说「打开微信」。")

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
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent == null) {
            return ToolResult.Text("「${app.label}」没有可启动的入口（可能是纯后台服务）。")
        }
        // 从非 Activity 上下文启动需要 NEW_TASK
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launchIntent)
            ToolResult.Text("已打开「${app.label}」")
        } catch (e: Exception) {
            ToolResult.Text("打开「${app.label}」失败：${e.message ?: e.javaClass.simpleName}")
        }
    }
}
