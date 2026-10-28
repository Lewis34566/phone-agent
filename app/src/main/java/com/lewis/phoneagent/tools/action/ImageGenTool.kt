package com.lewis.phoneagent.tools.action

import android.content.Context
import com.lewis.phoneagent.core.intent.RuleMatcher
import com.lewis.phoneagent.core.llm.ImageGenProvider
import com.lewis.phoneagent.tools.MatchScore
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolArgs
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.tools.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 生图工具：把"画一张XX"交给云端生图服务（用户配置的 OpenAI 兼容接口）。
 * 生成的图存 App 私有目录，聊天里以图片卡片展示。
 */
class ImageGenTool(
    private val context: Context,
    private val provider: ImageGenProvider,
) : Tool {

    override val spec = ToolSpec(
        name = "generate_image",
        description = "根据文字描述生成一张图片（需要用户已配置生图服务）",
    )

    override fun match(text: String): MatchScore? {
        val verbs = listOf("画一张", "画个", "画一幅", "画一个", "生成一张图", "生成图片", "生成一张", "文生图", "画")
        for (v in verbs) {
            val idx = text.indexOf(v)
            if (idx < 0) continue
            val prompt = text.substring(idx + v.length).trim().replace(Regex("^(一下|一张|一个)\\s*"), "")
            if (prompt.isEmpty()) continue
            return MatchScore(0.9f, mapOf("prompt" to prompt))
        }
        return null
    }

    override suspend fun execute(args: ToolArgs): ToolResult {
        val prompt = (args["prompt"] as? String)?.trim()
        if (prompt.isNullOrEmpty()) return ToolResult.Text("想画什么？试试说「画一张 海边日落」")

        val bytes = try {
            provider.generate(prompt, "1024x1024")
        } catch (e: Exception) {
            return ToolResult.Text("生图失败：${e.message ?: e.javaClass.simpleName}")
        }

        // 存到私有目录，聊天记录持久可用
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val file = File(dir, "gen_${System.currentTimeMillis()}.png")
        withContext(Dispatchers.IO) { file.writeBytes(bytes) }

        return ToolResult.Image(
            title = "已生成：$prompt",
            imagePath = file.absolutePath,
            note = "图片由你配置的云端生图服务生成，保存在本 App 私有目录。生视频能力暂未提供（成本高，后续版本）。",
        )
    }
}
