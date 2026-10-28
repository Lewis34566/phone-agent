package com.lewis.phoneagent.tools.notebook

import com.lewis.phoneagent.data.MistakeRepository
import com.lewis.phoneagent.tools.MatchScore
import com.lewis.phoneagent.tools.ParamSpec
import com.lewis.phoneagent.tools.ParamType
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolArgs
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.tools.ToolSpec

/**
 * 错题本工具④：按关键词找题（题目/答案/学科都搜）。
 * 关键词留空时返回最近存的题（浏览错题本）。
 */
class SearchMistakesTool(private val repository: MistakeRepository) : Tool {

    override val spec = ToolSpec(
        name = "search_mistakes",
        description = "在错题本里按关键词搜索（搜题目/答案/学科）；关键词为空时返回最近存入的错题",
        params = listOf(
            ParamSpec("keyword", ParamType.STRING, false, "搜索关键词"),
        ),
    )

    override fun match(text: String): MatchScore? {
        for (prefix in listOf("找错题", "搜错题", "错题搜索", "错题里找")) {
            val idx = text.indexOf(prefix)
            if (idx >= 0) {
                val keyword = text.substring(idx + prefix.length).trim()
                    .removePrefix("关于").removePrefix("：").removePrefix(":")
                return MatchScore(0.9f, mapOf("keyword" to keyword))
            }
        }
        return null
    }

    override suspend fun execute(args: ToolArgs): ToolResult {
        val keyword = (args["keyword"] as? String)?.trim().orEmpty()
        val notes = if (keyword.isEmpty()) repository.recent() else repository.search(keyword)
        if (notes.isEmpty()) {
            return ToolResult.Text(
                if (keyword.isEmpty()) "错题本还是空的。拍一张错题说「存到错题本」就能记下来。"
                else "错题本里没有找到包含「$keyword」的题。",
            )
        }
        return ToolResult.Text(
            buildString {
                append("找到 ${notes.size} 道题：\n")
                notes.forEachIndexed { i, n ->
                    append("${i + 1}. id=${n.id} [${n.subject}] ${n.question.take(50)}" +
                        "${if (n.question.length > 50) "…" else ""}\n")
                }
            },
        )
    }
}
