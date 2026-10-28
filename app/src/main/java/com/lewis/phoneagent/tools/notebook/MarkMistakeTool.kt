package com.lewis.phoneagent.tools.notebook

import com.lewis.phoneagent.data.MistakeRepository
import com.lewis.phoneagent.tools.MatchScore
import com.lewis.phoneagent.tools.ParamSpec
import com.lewis.phoneagent.tools.ParamType
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolArgs
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.tools.ToolSpec
import com.lewis.phoneagent.util.Format
import java.util.concurrent.TimeUnit

/**
 * 错题本工具③：记录复习结果（答对晋级、答错归零，走遗忘曲线）。
 *
 * 不做本地意图匹配（match 恒 null，LLM-only）：用户回答"答对了/不会"时，
 * 只有 LLM 知道当前正在复习的是哪一题的哪个 id。本地正则匹配会把结果记到
 * 错误的题上——错题本数据必须准，宁慢勿错。
 */
class MarkMistakeTool(private val repository: MistakeRepository) : Tool {

    override val spec = ToolSpec(
        name = "mark_reviewed",
        description = "记录一道错题的复习结果。correct=true 表示用户答对（复习间隔晋级），false 表示没记住（归零重来）",
        params = listOf(
            ParamSpec("id", ParamType.INT, true, "错题 id（review_due 结果里给出的 id）"),
            ParamSpec("correct", ParamType.BOOLEAN, true, "用户是否答对/记住了"),
        ),
    )

    override fun match(text: String): MatchScore? = null

    override suspend fun execute(args: ToolArgs): ToolResult {
        val id = when (val v = args["id"]) {
            is Int -> v.toLong()
            is String -> v.toLongOrNull() ?: return ToolResult.Text("错题 id 无效，没有记录。")
            else -> return ToolResult.Text("错题 id 无效，没有记录。")
        }
        val correct = when (val v = args["correct"]) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true) || v in listOf("对", "答对", "记得")
            else -> false
        }
        val note = repository.recordReview(id, correct)
            ?: return ToolResult.Text("没有找到 id=$id 的错题，没有记录。")
        val days = MistakeRepository.daysUntilNextReview(note.stage)
        val nextDate = Format.date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days.toLong()))
        return if (correct) {
            ToolResult.Text(
                "已记录 ✅ 第 $id 题答对，复习阶段 ${note.stage}/5，" +
                    "$days 天后（$nextDate）再复习。",
            )
        } else {
            ToolResult.Text(
                "已记录 📝 第 $id 题没记住（累计错 ${note.wrongCount} 次），" +
                    "重新从 1 天间隔开始，明天再复习。",
            )
        }
    }
}
