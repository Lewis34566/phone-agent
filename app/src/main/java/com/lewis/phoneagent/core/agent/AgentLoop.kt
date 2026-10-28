package com.lewis.phoneagent.core.agent

import com.lewis.phoneagent.core.LlmStreamEvent
import com.lewis.phoneagent.core.MemoryRecorder
import com.lewis.phoneagent.core.llm.LlmMessage
import com.lewis.phoneagent.core.llm.LlmNotConfiguredException
import com.lewis.phoneagent.core.llm.LlmProvider
import com.lewis.phoneagent.core.llm.LlmResult
import com.lewis.phoneagent.core.llm.LlmToolCall
import com.lewis.phoneagent.core.llm.ToolSchema
import com.lewis.phoneagent.model.ChatMessage
import com.lewis.phoneagent.model.ContentType
import com.lewis.phoneagent.model.Role
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolArgs
import com.lewis.phoneagent.tools.ToolRegistry
import com.lewis.phoneagent.tools.ToolResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Agent 循环（移植自手机版 Agent 的 tool-use 循环，去掉了 Termux 壳）：
 *
 *   LLM 主导 → 流式输出 → 模型决定调工具 → 执行（安全工具自动、危险工具先问人）
 *   → 结果回喂 → 模型看到结果继续推理下一步 → 循环，直到模型给出最终文本答复。
 *
 * 与旧版"排队执行"的本质区别：模型每一轮都能看到上一轮工具的真实结果，
 * 失败会自己换方案重试（重规划），而不是把一次性计划跑完交差。
 *
 * 熔断保护（移植自手机版 Agent 实测经验）：
 *   - 最大 MAX_ROUNDS 轮，防死循环烧钱
 *   - 连续 MAX_CONSECUTIVE_FAILS 轮工具全部失败 = 卡住了，停下并告诉用户，而不是烧完所有轮数
 */
class AgentLoop(private val registry: ToolRegistry) {

    companion object {
        const val MAX_ROUNDS = 10
        const val MAX_CONSECUTIVE_FAILS = 3
        const val HISTORY_LIMIT = 6
        const val TOOL_RESULT_MAX_CHARS = 2000

        /**
         * 系统提示词：必须保持字节级稳定，不要往里嵌日期、用户名等动态内容。
         * DeepSeek 的上下文缓存按前缀匹配，提示词 + 工具 schema 完全静态，
         * 每一轮循环、每一次对话都能命中缓存——首字延迟从 4-10 秒降到 ~0.1 秒。
         * 要改文案可以，但改完所有请求会冷启动一次。
         */
        val SYSTEM_PROMPT = """
你是 Lewis，用户手机上的自动化助手，用中文对话。用户用一句话描述要做的事，你调用工具把事情做完，然后把结果说清楚。

## 核心能力：错题本（拍照 → 自动入库 → 按遗忘曲线提醒复习）
- 用户拍照后，OCR 识别出的文字会出现在对话里（"识别图片中…"后面那条消息）。你要把题目、答案、学科整理出来，调用 add_mistake 存入错题本。
- 学科从内容判断（数学/英语/政治/专业课等），判断不出就填「未分类」，不要瞎猜。
- 用户说"复习错题/今天复习什么"时：先调 review_due 拿到今天到期的题，然后**一题一题**问用户"这道题记得吗？"，等用户回答后调 mark_reviewed（记得=correct true，没记住=correct false），再问下一题，全部过完再做小结。
- 复习提醒由系统定时发通知，你不用管提醒本身。

## 工作方式
1. 用户需求对应某个工具时，直接调用工具，不要先问"要不要我做"——安全操作（查/存/记/搜）当场做完再汇报。
2. 一句话包含多个需求时，一次返回多个工具调用。
3. 工具执行失败时，读错误信息，换一种方式重试最多 3 次；实在不行就如实告诉用户卡在哪。
4. 需要确认的操作（卸载应用等会改变手机状态的事）会自动弹确认卡片，等用户点了再执行。
5. 写作/总结/翻译/聊天类请求直接文本回答，不要硬套工具。
6. 不确定的（如"记一下"没说什么内容）就问一个最关键的问题，其余能推断的直接做。

## 其他可用能力
- note：记便签、查便签、删便签（离线可用）
- search_mistakes：在错题本里按关键词找题
- web_search：联网搜索（Bing/百度）
- 应用相关：list_apps、find_unused_apps、background_rank、storage_info、open_app 等
- vision：看图理解；generate_image：生图；play_game：小游戏

诚实边界：不编造能力，做不到的事直接说做不到。
""".trimIndent()
    }

    /**
     * 跑一轮完整 Agent 任务。
     *
     * @param approve 工具需要确认时回调（UI 出确认卡片），返回用户是否同意
     */
    suspend fun run(
        provider: LlmProvider,
        text: String,
        history: List<ChatMessage>,
        memoryRecorder: MemoryRecorder?,
        onEvent: suspend (LlmStreamEvent) -> Unit,
        approve: suspend (LlmStreamEvent.ToolCall) -> Boolean,
    ) {
        // 工具 schema 顺序 = 注册表顺序，固定不变 → 前缀缓存稳定命中
        val schemas = registry.all().map {
            ToolSchema.fromSpec(it.spec.name, it.spec.description, it.spec.params)
        }

        val messages = mutableListOf<LlmMessage>()
        messages += LlmMessage("system", SYSTEM_PROMPT)
        // 只带最近几轮文本（拍照的 OCR 文字也在里面），绝不塞无关数据
        history.filter { it.contentType == ContentType.TEXT }.takeLast(HISTORY_LIMIT).forEach {
            messages += LlmMessage(if (it.role == Role.USER) "user" else "assistant", it.content)
        }
        messages += LlmMessage("user", text)

        val fullText = StringBuilder() // 跨轮累积全部输出（UI 最终渲染整个任务过程）
        var consecutiveFails = 0

        for (round in 1..MAX_ROUNDS) {
            val roundText = StringBuilder()
            val result = try {
                provider.streamChat(messages, schemas) { delta ->
                    roundText.append(delta)
                    fullText.append(delta)
                    onEvent(LlmStreamEvent.Delta(delta))
                }
            } catch (e: LlmNotConfiguredException) {
                onEvent(LlmStreamEvent.Error("云端模型未配置：去设置页填写 baseUrl / apiKey / model。"))
                return
            } catch (e: Exception) {
                onEvent(LlmStreamEvent.Error(
                    "云端模型调用失败：${e.message ?: e.javaClass.simpleName}（已重试）。检查网络或设置页配置。",
                ))
                return
            }

            if (result is LlmResult.Text) {
                onEvent(LlmStreamEvent.FinalText(result.content.ifBlank { fullText.ifBlank { "（模型没有返回内容）" }.toString() }))
                return
            }

            val calls = when (result) {
                is LlmResult.ToolCall -> listOf(result)
                is LlmResult.MultiToolCall -> result.calls
                else -> emptyList()
            }
            if (calls.isEmpty()) {
                onEvent(LlmStreamEvent.FinalText(fullText.ifBlank { "（模型没有返回内容）" }.toString()))
                return
            }

            // 模型这轮的"思考"和工具清单作为 assistant 消息入上下文
            messages += LlmMessage(
                role = "assistant",
                content = roundText.toString(),
                toolCalls = calls.map { LlmToolCall(it.id, it.toolName, argsToJson(it.args)) },
            )

            var failCount = 0
            for (call in calls) {
                val toolResult = executeWithApproval(call, memoryRecorder, onEvent, approve)
                if (toolResult.isFailure) failCount++
                // 回喂：role=tool + tool_call_id，模型看到结果后推理下一步
                messages += LlmMessage("tool", toolResult.text, toolCallId = call.id)
            }

            consecutiveFails = if (calls.isNotEmpty() && failCount == calls.size) consecutiveFails + 1 else 0
            if (consecutiveFails >= MAX_CONSECUTIVE_FAILS) {
                onEvent(LlmStreamEvent.FinalText(
                    fullText.toString() +
                        "\n\n（连续 $MAX_CONSECUTIVE_FAILS 轮工具全部失败，疑似卡住，已自动停住。）",
                ))
                return
            }
        }

        onEvent(LlmStreamEvent.FinalText(
            fullText.ifBlank { "（模型没有返回内容）" }.toString() +
                "\n\n（达到最大轮数 $MAX_ROUNDS，已自动停住。可以说「继续」接着做。）",
        ))
    }

    /** 执行一个工具调用：危险工具先弹确认，安全工具自动执行；永不抛异常，失败转文本回喂 */
    private suspend fun executeWithApproval(
        call: LlmResult.ToolCall,
        memoryRecorder: MemoryRecorder?,
        onEvent: suspend (LlmStreamEvent) -> Unit,
        approve: suspend (LlmStreamEvent.ToolCall) -> Boolean,
    ): ExecuteOutcome {
        val tool = registry.byName(call.toolName)
        if (tool == null) {
            return ExecuteOutcome("错误：没有「${call.toolName}」这个工具，请换一个能力。", isFailure = true)
        }

        if (tool.spec.needConfirm) {
            val actionId = UUID.randomUUID().toString().substring(0, 8)
            val (title, description) = tool.confirmPrompt(call.args)
            val approved = approve(LlmStreamEvent.ToolCall(actionId, title, description))
            if (!approved) return ExecuteOutcome("用户取消了该操作。", isFailure = false)
        }

        onEvent(LlmStreamEvent.Delta("\n🔧 ${tool.spec.name}…"))
        return try {
            val result = tool.execute(call.args)
            memoryRecorder?.record(tool.spec.name, call.args)
            ExecuteOutcome(result.toText(), isFailure = false)
        } catch (e: Exception) {
            ExecuteOutcome(
                "执行失败：${e.message ?: e.javaClass.simpleName}",
                isFailure = true,
            )
        }
    }

    /** 工具参数 → JSON 字符串（回喂上下文里的 assistant 消息用） */
    private fun argsToJson(args: ToolArgs): String = buildJsonObject {
        args.forEach { (k, v) ->
            when (v) {
                null -> put(k, "")
                is String -> put(k, v)
                is Number -> put(k, v.toString())
                is Boolean -> put(k, v)
                else -> put(k, v.toString())
            }
        }
    }.toString()

    private data class ExecuteOutcome(val text: String, val isFailure: Boolean)
}

/** 工具结果 → 给 LLM 看的纯文本（Agent 循环回喂用；超长截断，控制上下文膨胀） */
fun ToolResult.toText(): String = when (this) {
    is ToolResult.Text -> text.take(AgentLoop.TOOL_RESULT_MAX_CHARS)
    is ToolResult.AppList -> buildString {
        append("「$title」共 ${apps.size} 个应用：")
        apps.take(30).forEach { append("\n- ${it.label}（${it.packageName}）") }
        note?.let { append("\n$it") }
    }
    is ToolResult.Ranking -> buildString {
        append("「$title」：")
        rows.take(30).forEach { append("\n- ${it.title}：${it.subtitle}") }
        note?.let { append("\n$it") }
    }
    is ToolResult.NeedPermission -> "需要授权才能继续：$message"
    is ToolResult.Image -> "已生成/返回图片：$title（$imagePath）"
    is ToolResult.Game -> "已开启小游戏「$title」"
}
