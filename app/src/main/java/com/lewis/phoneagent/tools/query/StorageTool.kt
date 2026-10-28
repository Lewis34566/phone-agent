package com.lewis.phoneagent.tools.query

import android.os.Environment
import android.os.StatFs
import com.lewis.phoneagent.core.intent.RuleMatcher
import com.lewis.phoneagent.data.AppRepository
import com.lewis.phoneagent.tools.MatchScore
import com.lewis.phoneagent.tools.RankRow
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolArgs
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.tools.ToolSpec
import com.lewis.phoneagent.util.Format

/**
 * 查询工具⑤：存储空间管家。
 * 总容量/已用/剩余（StatFs，无需任何权限）+ 占用最大的应用排行（APK 大小近似）。
 */
class StorageTool(private val repository: AppRepository) : Tool {

    override val spec = ToolSpec(
        name = "storage_info",
        description = "查看手机存储空间使用情况和占用最大的应用排行",
    )

    override fun match(text: String): MatchScore? {
        val score = RuleMatcher.keywordScore(
            text,
            strong = listOf(
                "存储空间", "空间不够", "内存不足", "空间还有多少", "存储", "容量", "空间满了",
            ),
            weak = listOf("空间"),
        ) ?: return null
        return MatchScore(score)
    }

    override suspend fun execute(args: ToolArgs): ToolResult {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.totalBytes
        val available = stat.availableBytes
        val used = total - available
        val percent = if (total > 0) used * 100 / total else 0

        val apps = repository.apps().sortedByDescending { it.apkSizeBytes ?: 0 }.take(10)
        val rows = apps.map { app ->
            RankRow(
                title = app.label,
                subtitle = Format.bytes(app.apkSizeBytes ?: 0),
                packageName = app.packageName,
            )
        }

        return ToolResult.Ranking(
            title = "存储空间：已用 ${Format.bytes(used)} / ${Format.bytes(total)}（$percent%），剩余 ${Format.bytes(available)}",
            rows = rows,
            note = "排行按 APK 文件大小估算（不含数据/缓存）；完整占用需「使用情况访问」授权。",
        )
    }
}
