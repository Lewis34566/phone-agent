package com.lewis.phoneagent.tools.query

import com.lewis.phoneagent.core.intent.RuleMatcher
import com.lewis.phoneagent.core.llm.VisionProvider
import com.lewis.phoneagent.data.ImageMemory
import com.lewis.phoneagent.tools.MatchScore
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolArgs
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.tools.ToolSpec

/**
 * 看图理解（Phone-Vibe 拍照看图能力）：对最近一张图片提问/描述。
 * 图片来自拍照（ImageMemory 里记录的最新一张），理解交给用户配置的多模态模型。
 * 诚实边界：DeepSeek 不支持图片，需在设置页配置视觉模型（如通义 qwen-vl-plus）。
 */
class VisionTool(
    private val imageMemory: ImageMemory,
    private val provider: VisionProvider,
) : Tool {

    override val spec = ToolSpec(
        name = "describe_image",
        description = "对最近拍摄的图片进行描述或问答",
    )

    override fun match(text: String): MatchScore? {
        val score = RuleMatcher.keywordScore(
            text,
            strong = listOf(
                "这张图", "图片里", "图里", "看图", "描述这张", "照片里", "这图",
            ),
        ) ?: return null
        return MatchScore(score, mapOf("question" to text))
    }

    override suspend fun execute(args: ToolArgs): ToolResult {
        val imagePath = imageMemory.latest()
        if (imagePath == null) {
            return ToolResult.Text("还没有图片可看。先用 📷 拍一张，再说「这张图是什么」。")
        }
        val question = (args["question"] as? String)?.trim().orEmpty()
        return try {
            // 一次性取回描述（看图回答较短，不做流式）
            var answer = ""
            provider.describe(imagePath, question) { delta -> answer += delta }
            ToolResult.Text(answer.ifBlank { "（看图服务没有返回内容）" })
        } catch (e: Exception) {
            ToolResult.Text("看图失败：${e.message ?: e.javaClass.simpleName}")
        }
    }
}
