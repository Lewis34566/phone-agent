package com.lewis.phoneagent.tools.query

import com.lewis.phoneagent.core.intent.RuleMatcher
import com.lewis.phoneagent.data.AppRepository
import com.lewis.phoneagent.tools.MatchScore
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolArgs
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.tools.ToolSpec
import com.lewis.phoneagent.util.Format

/**
 * 查询工具①：列出已安装应用（名称、版本、安装时间、APK 大小）。
 * 无需任何权限；扫描在后台协程完成，结果走内存缓存。
 */
class ListAppsTool(private val repository: AppRepository) : Tool {

    override val spec = ToolSpec(
        name = "list_apps",
        description = "列出手机上已安装的应用：名称、版本、安装时间、占用大小",
    )

    override fun match(text: String): MatchScore? {
        val score = RuleMatcher.keywordScore(
            text,
            strong = listOf(
                "已装应用", "应用列表", "应用清单", "安装了哪些", "装了哪些",
                "有哪些应用", "所有应用", "全部应用", "看看应用", "手机上的软件", "装了哪些软件",
            ),
        ) ?: return null
        // "打开X"、"卸载X"由操作类工具处理，这里只做纯查询
        if (text.contains("打开") || text.contains("卸载") || text.contains("删除")) return null
        // "所有/全部/包括系统" → 全量口径（含无图标的系统/后台应用）
        val includeAll = text.contains("所有") || text.contains("全部") || text.contains("包括系统")
        return MatchScore(score, mapOf("all" to includeAll))
    }

    override suspend fun execute(args: ToolArgs): ToolResult {
        val includeAll = args["all"] == true
        val apps = repository.apps(includeAll = includeAll)
        if (apps.isEmpty()) return ToolResult.Text("没有扫描到任何应用。")

        val totalBytes = apps.sumOf { it.apkSizeBytes ?: 0L }
        return ToolResult.AppList(
            title = "已安装应用（${apps.size} 个）",
            apps = apps,
            note = "按安装时间排序；大小为 APK 文件大小，共约 ${Format.bytes(totalBytes)}。" +
                (if (includeAll) "本次包含全部应用（含无图标的系统组件）。" else "只列有桌面图标的应用，说「列出所有应用」看全量。") +
                "点应用可打开/跳设置/卸载。完整占用（含数据/缓存）需授权「使用情况访问」。",
        )
    }
}
