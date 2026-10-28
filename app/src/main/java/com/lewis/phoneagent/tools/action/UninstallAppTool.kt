package com.lewis.phoneagent.tools.action

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import com.lewis.phoneagent.core.intent.AppNameResolver
import com.lewis.phoneagent.core.intent.RuleMatcher
import com.lewis.phoneagent.tools.MatchScore
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolArgs
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.tools.ToolSpec

/**
 * 操作工具②：卸载应用（needConfirm = true，执行前必须出确认卡片）。
 *
 * 诚实边界（核心）：
 *  - 绝不静默卸载。流程：我方确认卡片 → 系统卸载确认界面 → 用户在系统界面最后拍板。
 *  - 普通 App 无权直接删除其他应用，只能调起系统卸载流程，这是 Android 的安全设计。
 *  - 静默卸载需要系统签名/root/Shizuku，属于 PrivilegedTool 预留扩展，不实现。
 */
class UninstallAppTool(
    private val context: Context,
    private val resolver: AppNameResolver,
) : Tool {

    override val spec = ToolSpec(
        name = "uninstall_app",
        description = "卸载指定应用（跳转系统卸载界面，由用户最终确认）",
        needConfirm = true,
    )

    override fun match(text: String): MatchScore? {
        val name = RuleMatcher.extractActionTarget(
            text,
            verbs = listOf("卸载", "删除", "卸掉", "卸了"),
        ) ?: return null
        // 要求句子确实带动作意图（"删除"等词本身已够）
        return MatchScore(0.9f, mapOf("appName" to name))
    }

    /** 卸载的确认卡片文案：说清楚后果 */
    override fun confirmPrompt(args: ToolArgs): Pair<String, String> {
        val name = (args["appName"] as? String) ?: "该应用"
        return "卸载「$name」？" to
            "点「确认执行」后将打开系统卸载界面，由你在系统弹窗中最后拍板。" +
            "卸载会删除该应用及其全部数据，且无法撤销。"
    }

    override suspend fun execute(args: ToolArgs): ToolResult {
        val name = args["appName"] as? String
        if (name.isNullOrBlank()) return ToolResult.Text("想卸载哪个应用？试试说「卸载XX」。")

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
        return launchSystemUninstall(candidates.first())
    }

    /** 调起系统卸载界面；系统应用无法卸载时诚实说明 */
    private fun launchSystemUninstall(app: com.lewis.phoneagent.model.AppInfo): ToolResult {
        if (app.isSystem) {
            return ToolResult.Text(
                "「${app.label}」是系统应用，普通方式无法卸载（卸载系统应用需要 root/Shizuku，属于预留扩展）。" +
                    "可以在它的设置页里「停用」代替——说「打开${app.label}的设置」。",
            )
        }
        // 首选 ACTION_UNINSTALL_PACKAGE（需 REQUEST_DELETE_PACKAGES，已在 Manifest 声明）；
        // 个别 ROM 不支持时回退 ACTION_DELETE。两者都会弹系统确认界面，绝不静默。
        val intents = listOf(
            Intent(Intent.ACTION_UNINSTALL_PACKAGE).setData(Uri.parse("package:${app.packageName}")),
            Intent(Intent.ACTION_DELETE).setData(Uri.parse("package:${app.packageName}")),
        )
        for (intent in intents) {
            try {
                context.startActivity(intent)
                return ToolResult.Text("已打开系统卸载界面，请在系统弹窗中确认卸载「${app.label}」。")
            } catch (_: Exception) {
                // 尝试下一个 intent
            }
        }
        return ToolResult.Text("无法调起系统卸载界面，请到系统设置 → 应用管理里手动卸载「${app.label}」。")
    }
}
