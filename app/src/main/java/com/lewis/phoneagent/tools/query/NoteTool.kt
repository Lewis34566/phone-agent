package com.lewis.phoneagent.tools.query

import com.lewis.phoneagent.core.intent.RuleMatcher
import com.lewis.phoneagent.data.db.NoteDao
import com.lewis.phoneagent.data.db.NoteEntity
import com.lewis.phoneagent.tools.MatchScore
import com.lewis.phoneagent.tools.RankRow
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolArgs
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.tools.ToolSpec
import com.lewis.phoneagent.util.Format

/**
 * 便签工具（办公能力）：记便签 / 查便签 / 删便签，全部本机完成，离线可用。
 */
class NoteTool(private val noteDao: NoteDao) : Tool {

    override val spec = ToolSpec(
        name = "note",
        description = "记便签、查看便签列表、删除便签",
    )

    override fun match(text: String): MatchScore? {
        // 记：记一下/记个笔记/记一笔/备忘 + 内容
        for (prefix in listOf("记一下", "记个笔记", "记一笔", "记个备忘", "备忘", "记下", "帮我记")) {
            val idx = text.indexOf(prefix)
            if (idx >= 0) {
                val content = text.substring(idx + prefix.length).trim().removePrefix("：").removePrefix(":")
                if (content.isNotEmpty()) {
                    return MatchScore(0.9f, mapOf("action" to "add", "content" to content))
                }
            }
        }
        // 查：我的便签/便签列表/有什么笔记/我的笔记
        if (RuleMatcher.keywordScore(
                text,
                strong = listOf("我的便签", "便签列表", "我的笔记", "有什么笔记", "查看便签", "记事本"),
            ) != null
        ) {
            return MatchScore(0.9f, mapOf("action" to "list"))
        }
        // 删：删除便签/删掉笔记 + 关键词（或全部）
        for (prefix in listOf("删除便签", "删掉便签", "删除笔记", "清除便签")) {
            val idx = text.indexOf(prefix)
            if (idx >= 0) {
                val keyword = text.substring(idx + prefix.length).trim()
                    .removePrefix("关于").removePrefix("：").removePrefix(":")
                return MatchScore(0.9f, mapOf("action" to "delete", "keyword" to keyword))
            }
        }
        return null
    }

    override suspend fun execute(args: ToolArgs): ToolResult {
        return when (args["action"]) {
        "add" -> {
            val content = (args["content"] as? String)?.trim().orEmpty()
            if (content.isEmpty()) return ToolResult.Text("想记什么？试试说「记一下 明天交报告」")
            noteDao.insert(NoteEntity(content = content))
            ToolResult.Text("已记下 📝：$content")
        }

        "list" -> {
            val notes = noteDao.recent(30)
            if (notes.isEmpty()) return ToolResult.Text("还没有便签。说「记一下XX」就能随手记。")
            ToolResult.Ranking(
                title = "我的便签（${notes.size} 条）",
                rows = notes.map { RankRow(it.content.take(40), Format.date(it.createTime)) },
                note = "说「删除便签XX」可删除包含该词的所有便签；「删除便签全部」清空。",
            )
        }

        "delete" -> {
            val keyword = (args["keyword"] as? String)?.trim().orEmpty()
            val deleted = if (keyword.isEmpty() || keyword == "全部" || keyword == "所有") {
                noteDao.clear()
                "已清空全部便签"
            } else {
                val n = noteDao.deleteByKeyword(keyword)
                "已删除 $n 条包含「$keyword」的便签"
            }
            ToolResult.Text(deleted)
        }

        else -> ToolResult.Text("便签用法：记一下XX / 我的便签 / 删除便签XX")
        }
    }
}
