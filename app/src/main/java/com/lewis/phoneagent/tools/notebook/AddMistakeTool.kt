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
 * 错题本工具①：存入错题（拍照 OCR 的文字由 Agent 循环整理后调用）。
 *
 * 不做本地意图匹配（match 恒 null，LLM-only）：题目内容在对话上下文的
 * OCR 文字里，本地规则无法从整段对话中提取，只有 LLM 能整理。
 */
class AddMistakeTool(private val repository: MistakeRepository) : Tool {

    override val spec = ToolSpec(
        name = "add_mistake",
        description = "把一道错题存入错题本。拍照后 OCR 文字在对话里，整理出题目/答案/学科后调用此工具",
        params = listOf(
            ParamSpec("question", ParamType.STRING, true, "题目内容"),
            ParamSpec("answer", ParamType.STRING, false, "正确答案或解题要点（没有可留空）"),
            ParamSpec("subject", ParamType.STRING, false, "学科（数学/英语/政治/专业课等，判断不出填「未分类」）"),
        ),
    )

    override fun match(text: String): MatchScore? = null

    override suspend fun execute(args: ToolArgs): ToolResult {
        val question = (args["question"] as? String)?.trim().orEmpty()
        if (question.isEmpty()) return ToolResult.Text("题目内容为空，没有存入。请带上题目内容再说一次。")
        val answer = (args["answer"] as? String)?.trim().orEmpty()
        val subject = (args["subject"] as? String)?.trim().orEmpty().ifBlank { "未分类" }
        val id = repository.add(question, answer, subject)
        val total = repository.total()
        return ToolResult.Text(
            buildString {
                append("已存入错题本 ✅（id=$id，共 $total 题）\n")
                append("学科：$subject\n")
                append("题目：${question.take(60)}${if (question.length > 60) "…" else ""}\n")
                if (answer.isNotEmpty()) append("答案：${answer.take(60)}${if (answer.length > 60) "…" else ""}\n")
                append("明天会提醒第一次复习。")
            },
        )
    }
}
