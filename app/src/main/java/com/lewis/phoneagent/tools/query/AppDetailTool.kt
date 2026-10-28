package com.lewis.phoneagent.tools.query

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.lewis.phoneagent.core.intent.AppNameResolver
import com.lewis.phoneagent.core.intent.RuleMatcher
import com.lewis.phoneagent.data.AppRepository
import com.lewis.phoneagent.data.PermissionChecker
import com.lewis.phoneagent.data.UsageRepository
import com.lewis.phoneagent.tools.MatchScore
import com.lewis.phoneagent.tools.RankRow
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolArgs
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.tools.ToolSpec
import com.lewis.phoneagent.util.Format

/**
 * 应用详情：一个应用的全部可获取信息汇总（软件全管理的核心查询）。
 * 版本/安装时间/APK大小/权限数（无需权限）+ 完整占用/最近使用（需使用情况访问）。
 */
class AppDetailTool(
    private val context: Context,
    private val resolver: AppNameResolver,
    private val repository: AppRepository,
    private val usage: UsageRepository,
    private val checker: PermissionChecker,
) : Tool {

    override val spec = ToolSpec(
        name = "app_detail",
        description = "查看指定应用的详细信息（版本、大小、权限数、使用情况）",
    )

    override fun match(text: String): MatchScore? {
        // 带应用名的详情句式；纯"权限/设置"类由其他工具处理
        if (text.contains("权限") || text.contains("设置")) return null
        for (prefix in listOf("详细信息", "详情", "信息")) {
            val idx = text.indexOf(prefix)
            if (idx >= 0) {
                val name = text.substring(0, idx)
                    .replace(Regex("^(查看|看看|看|查|查查|问问)\\s*"), "")
                    .replace(Regex("的\\s*$"), "")
                    .trim()
                if (name.isNotEmpty()) return MatchScore(0.85f, mapOf("appName" to name))
            }
        }
        // "XX多大" "XX占多少空间"
        val m = Regex("^(.{1,20}?)(多大|占多少|占多大|多少空间)$").find(text.trim())
        if (m != null) return MatchScore(0.85f, mapOf("appName" to m.groupValues[1]))
        return null
    }

    override suspend fun execute(args: ToolArgs): ToolResult {
        val name = (args["appName"] as? String)?.trim()
        if (name.isNullOrEmpty()) return ToolResult.Text("想看哪个应用的详情？试试说「微信的详细信息」")

        val candidates = resolver.resolve(name)
        if (candidates.isEmpty()) return ToolResult.Text("没找到叫「$name」的应用，换个准确的名字试试？")
        if (candidates.size > 1) {
            return ToolResult.Text(
                "「$name」可能指：" + candidates.take(5).joinToString("、") { it.label } + "。请说完整名字。",
            )
        }
        val app = candidates.first()
        val pm = context.packageManager

        val rows = mutableListOf<RankRow>()
        rows += RankRow("包名", app.packageName)
        rows += RankRow("版本", app.versionName ?: "未知")
        rows += RankRow("安装时间", Format.date(app.installTimeMs))
        if (app.lastUpdateTimeMs != app.installTimeMs) {
            rows += RankRow("最近更新", Format.date(app.lastUpdateTimeMs))
        }
        rows += RankRow("APK 大小", Format.bytes(app.apkSizeBytes ?: 0))
        rows += RankRow("类型", if (app.isSystem) "系统应用" else "用户安装")

        // 权限数量（无需权限）
        try {
            val pi: PackageInfo = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
            rows += RankRow("申请权限", "${pi.requestedPermissions?.size ?: 0} 项（说「查看${app.label}的权限」看明细）")
        } catch (_: Exception) {
        }

        // 需授权的信息：完整占用 + 最近使用
        if (checker.hasUsageAccess()) {
            val total = usage.totalSizeBytes(app.packageName)
            if (total != null) rows += RankRow("完整占用", Format.bytes(total))
            val stats = usage.stats(days = 365)[app.packageName]
            if (stats != null) {
                rows += RankRow("最近使用", Format.ago(stats.lastUsed))
                rows += RankRow("一年前台时长", Format.duration(stats.foregroundMs))
            }
        }

        return ToolResult.Ranking(
            title = "「${app.label}」详情",
            rows = rows,
            note = if (checker.hasUsageAccess()) null
            else "完整占用/使用记录需「使用情况访问」授权（设置页可开）。点应用行弹操作菜单可打开/设置/卸载。",
        )
    }
}
