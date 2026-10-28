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
 * 查询工具④：后台活动最多的应用排行。
 *
 * 诚实边界（重要）：
 *  - 普通 App 永远拿不到系统级电量统计（BATTERY_STATS 是系统权限），
 *    真实"耗电排行"需要 Shizuku/root（预留 PrivilegedTool 扩展，不实现）。
 *  - 本工具用「使用情况访问」的事件流推算后台活动时长，作为近似参考，
 *    结果 note 里明确标注这不是真实耗电量。
 */
class BackgroundRankTool(
    private val repository: AppRepository,
    private val usage: UsageRepository,
    private val checker: PermissionChecker,
) : Tool {

    override val spec = ToolSpec(
        name = "background_rank",
        description = "列出后台活动较多的应用排行（基于使用情况记录的近似估算）",
    )

    override fun match(text: String): MatchScore? {
        val score = RuleMatcher.keywordScore(
            text,
            strong = listOf(
                "后台活动", "后台运行", "后台多", "耗电", "费电", "电量杀手", "偷电", "耗电多",
            ),
            weak = listOf("后台"),
        ) ?: return null
        return MatchScore(score)
    }

    override suspend fun execute(args: ToolArgs): ToolResult {
        if (!checker.hasUsageAccess()) {
            return ToolResult.NeedPermission(
                permission = RequiredPermission.USAGE_ACCESS,
                message = "分析后台活动需要「使用情况访问」权限（读取系统应用活动记录）。" +
                    "点「去授权」跳转到系统设置开启，回来后重新说一遍即可。",
            )
        }

        val apps = repository.apps()
        val bg = usage.backgroundDurationMs(days = 7)
        if (bg.isEmpty()) {
            return ToolResult.Text("近 7 天没有足够的后台活动记录。刚授权的话，使用几天后再问会有更准的数据。")
        }

        // 排序取前 15，过滤后台不足 1 分钟的噪音
        val rows = bg.entries
            .mapNotNull { (pkg, ms) ->
                if (ms < 60_000) return@mapNotNull null
                val app = apps.find { it.packageName == pkg } ?: return@mapNotNull null
                Triple(app, ms, RankRow(app.label, "后台活动 ${Format.duration(ms)}", pkg))
            }
            .sortedByDescending { it.second }
            .take(15)
            .map { it.third }

        if (rows.isEmpty()) {
            return ToolResult.Text("近 7 天没有后台活动明显的应用 🎉")
        }

        return ToolResult.Ranking(
            title = "近 7 天后台活动最多的应用（${rows.size} 个）",
            rows = rows,
            note = "基于「使用情况访问」事件推算的后台活动时长，是近似参考，不等同于真实耗电量。" +
                "真实耗电数据需要系统级权限（预留 Shizuku 扩展，暂未实现）。",
        )
    }
}
