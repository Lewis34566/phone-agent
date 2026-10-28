package com.lewis.phoneagent.tools.query

import com.lewis.phoneagent.core.intent.RuleMatcher
import com.lewis.phoneagent.data.AppRepository
import com.lewis.phoneagent.data.PermissionChecker
import com.lewis.phoneagent.data.UsageRepository
import com.lewis.phoneagent.tools.MatchScore
import com.lewis.phoneagent.tools.RankRow
import com.lewis.phoneagent.tools.RequiredPermission
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolArgs
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.tools.ToolSpec
import com.lewis.phoneagent.util.Format

/**
 * 查询工具②：按最后使用时间找出长期没用的应用。
 * 依赖「使用情况访问」权限：未授权时返回 NeedPermission 引导卡片，绝不静默失败。
 */
class UnusedAppsTool(
    private val repository: AppRepository,
    private val usage: UsageRepository,
    private val checker: PermissionChecker,
) : Tool {

    override val spec = ToolSpec(
        name = "find_unused_apps",
        description = "找出长期没有使用过的应用，按最后使用时间排序",
    )

    override fun match(text: String): MatchScore? {
        val score = RuleMatcher.keywordScore(
            text,
            strong = listOf(
                "很久没用", "长期没用", "太久没用", "僵尸", "吃灰", "没用过",
                "没用的应用", "没用的软件", "清理建议", "多久没用",
            ),
            weak = listOf("没用", "不用的应用"),
        ) ?: return null
        return MatchScore(score, mapOf("days" to RuleMatcher.parseUnusedDays(text)))
    }

    override suspend fun execute(args: ToolArgs): ToolResult {
        if (!checker.hasUsageAccess()) {
            return ToolResult.NeedPermission(
                permission = RequiredPermission.USAGE_ACCESS,
                message = "找出长期没用的应用，需要「使用情况访问」权限（系统用它记录每个应用最后使用的时间）。" +
                    "点下面的「去授权」跳转到系统设置开启，回来后重新说一遍即可。",
            )
        }

        val days = (args["days"] as? Int) ?: 30
        val apps = repository.apps()
        val stats = usage.stats(days = 365) // 取一年内的使用记录做判断
        val now = System.currentTimeMillis()
        val threshold = now - days * 24L * 3600_000

        // 筛选：从未使用（无记录）或最后使用早于阈值
        val unused = apps.mapNotNull { app ->
            val last = stats[app.packageName]?.lastUsed
            when {
                last == null -> app to "从未使用"
                last < threshold -> app to "最后使用：${Format.ago(last, now)}"
                else -> null
            }
        }.sortedBy { stats[it.first.packageName]?.lastUsed ?: Long.MIN_VALUE }

        if (unused.isEmpty()) {
            return ToolResult.Text("真不错：最近 $days 天内没有闲置的应用 🎉")
        }

        return ToolResult.Ranking(
            title = "超过 $days 天没用的应用（${unused.size} 个）",
            rows = unused.take(30).map { (app, desc) ->
                RankRow(app.label, desc, app.packageName)
            },
            note = "口径：系统「使用情况访问」记录的最后使用时间；从未启动过的应用归入「从未使用」。" +
                "部分手机系统只保留有限时间的使用记录。点应用可跳到它的设置页（阶段③接入）。",
        )
    }
}
