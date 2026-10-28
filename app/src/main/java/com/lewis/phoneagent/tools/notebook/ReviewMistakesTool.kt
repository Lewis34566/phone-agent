package com.lewis.phoneagent.tools.notebook

import com.lewis.phoneagent.core.intent.RuleMatcher
import com.lewis.phoneagent.data.MistakeRepository
import com.lewis.phoneagent.tools.MatchScore
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolArgs
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.tools.ToolSpec

/**
 * 错题本工具②：查询今天到期待复习的题（复习流程第一步）。
 * 拿到列表后由 Agent 一题一题问用户，用户答完调用 mark_reviewed。
 */
class ReviewMistakesTool(private val repository: MistakeRepository) : Tool {

    override val spec = ToolSpec(
        name = "review_due",
        description = "查询今天到期待复习的错题。拿到列表后一题一题问用户记得不记得，用户回答后调 mark_reviewed 记录",
    )

    override fun match(text: String): MatchScore? {
        if (RuleMatcher.keywordScore(
                text,
                strong = listOf("复习错题", "今天复习", "待复习", "错题复习", "要复习", "复习什么"),
            ) != null
        ) {
            return MatchScore(0.9f)
        }
        return null
    }

    override suspend fun execute(args: ToolArgs): ToolResult {
        val due = repository.due()
        val total = repository.total()
        if (due.isEmpty()) {
            return ToolResult.Text("今天没有到期的错题。错题本共 $total 题。")
        }
        // 输出对 LLM 结构化：id 是关键，答完用 id 调 mark_reviewed
        return ToolResult.Text(
            buildString {
                append("今天有 ${due.size} 道题到期：\n")
                due.forEachIndexed { i, n ->
                    append("${i + 1}. id=${n.id} [${n.subject}] ${n.question.take(50)}" +
                        "${if (n.question.length > 50) "…" else ""}\n")
                }
                append("\n请一题一题问用户，等用户回答后再调 mark_reviewed(id, correct)。")
            },
        )
    }
}
