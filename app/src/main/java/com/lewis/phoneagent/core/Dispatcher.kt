package com.lewis.phoneagent.core

import com.lewis.phoneagent.core.agent.AgentLoop
import com.lewis.phoneagent.core.confirm.ConfirmFlow
import com.lewis.phoneagent.core.llm.LlmProvider
import com.lewis.phoneagent.model.ChatMessage
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolRegistry
import com.lewis.phoneagent.tools.ToolResult

/**
 * 调度层核心，两条路：
 *   1. 本地快速通道：常见指令由工具自带 match() 打分命中（离线、<300ms）；
 *   2. Agent 循环：未命中交给 LLM 主导的多轮工具循环（安全工具自动执行，
 *      危险工具弹确认卡片，结果回喂、失败重规划——见 [AgentLoop]）。
 */
class Dispatcher(
    private val registry: ToolRegistry,
    private val confirmFlow: ConfirmFlow = ConfirmFlow(),
    private val llmProvider: LlmProvider? = null,
    private val memoryRecorder: MemoryRecorder? = null,
) {

    /** 本地规则命中的最低分阈值（0~1），低于此分认为没听懂，走 Agent 循环 */
    companion object {
        const val MATCH_THRESHOLD = 0.6f
    }

    private val agentLoop by lazy { AgentLoop(registry) }

    /** 处理一条用户输入，返回给 UI 的响应（本地快速通道） */
    suspend fun process(text: String): DispatcherResponse {
        val best = registry.matchBest(text)
        if (best != null && best.score >= MATCH_THRESHOLD) {
            val tool = best.tool
            return if (tool.spec.needConfirm) {
                val actionId = confirmFlow.register(tool, best.args)
                val (title, description) = tool.confirmPrompt(best.args)
                DispatcherResponse.ConfirmRequired(actionId, title, description)
            } else {
                executeTool(tool, best.args)
            }
        }
        // 本地未命中：交给 ViewModel 走 Agent 循环（processAgent）
        return DispatcherResponse.Placeholder(text)
    }

    /**
     * Agent 循环入口：本地未命中时调用，LLM 主导直到任务完成。
     * 事件流：
     *  - Delta     ：模型输出增量文本（UI 流式渲染；跨轮累积，工具执行过程也有进度行）
     *  - FinalText ：任务完成的最终全文
     *  - ToolCall  ：模型要执行危险操作 → UI 出确认卡片，approve 回调等用户决定
     *  - Error     ：未配置/网络失败（已自动重试）→ UI 出错误提示
     */
    suspend fun processAgent(
        text: String,
        history: List<ChatMessage>,
        onEvent: suspend (LlmStreamEvent) -> Unit,
        approve: suspend (LlmStreamEvent.ToolCall) -> Boolean,
    ) {
        val provider = llmProvider
        if (provider == null) {
            onEvent(LlmStreamEvent.Error(
                "云端模型未配置。去右上角设置页填 baseUrl / apiKey / model（默认 DeepSeek）后，" +
                    "本地认不出的需求就会交给 Lewis 自动完成。本地指令不受影响。",
            ))
            return
        }
        agentLoop.run(provider, text, history, memoryRecorder, onEvent, approve)
    }

    /** 用户在确认卡片上点「确认执行」；多步任务顺序执行并汇总 */
    suspend fun confirm(actionId: String): DispatcherResponse {
        val action = confirmFlow.take(actionId)
            ?: return DispatcherResponse.Text(
                "此操作已失效（可能因为 App 重启过），请重新说一遍。",
                DispatcherResponse.Source.LOCAL_RULE,
            )
        if (action.steps.size == 1) {
            return executeTool(action.steps[0].tool, action.steps[0].args)
        }
        // 多步任务：顺序执行，每步结果汇总成报告
        val summary = buildString {
            action.steps.forEachIndexed { i, step ->
                val result = try {
                    step.tool.execute(step.args)
                } catch (e: Exception) {
                    ToolResult.Text("出错：${e.message ?: e.javaClass.simpleName}")
                }
                memoryRecorder?.record(step.tool.spec.name, step.args)
                append("${i + 1}. ${summarize(result)}")
                if (i != action.steps.lastIndex) append("\n\n")
            }
        }
        return DispatcherResponse.ToolOutcome(
            "multi_task",
            ToolResult.Text("任务完成，共 ${action.steps.size} 步：\n\n$summary"),
        )
    }

    /** 工具结果 → 一句话摘要（多步任务报告用） */
    private fun summarize(result: ToolResult): String = when (result) {
        is ToolResult.Text -> result.text.take(200)
        is ToolResult.AppList -> "「${result.title}」共 ${result.apps.size} 个应用"
        is ToolResult.Ranking -> "「${result.title}」共 ${result.rows.size} 项"
        is ToolResult.NeedPermission -> "需要授权才能继续：${result.message.take(60)}…"
        is ToolResult.Image -> result.title
        is ToolResult.Game -> "已开启小游戏「${result.title}」"
    }

    /** 用户在确认卡片上点「取消」 */
    fun cancel(actionId: String) {
        confirmFlow.cancel(actionId)
    }

    /** 执行工具并包装成响应；工具抛异常时转为文本错误，绝不崩溃 */
    private suspend fun executeTool(tool: Tool, args: Map<String, Any?>): DispatcherResponse {
        val result = try {
            tool.execute(args)
        } catch (e: Exception) {
            ToolResult.Text("执行「${tool.spec.name}」时出错了：${e.message ?: e.javaClass.simpleName}")
        }
        // 记录用户习惯到全局记忆（后台协程不阻塞响应）
        memoryRecorder?.record(tool.spec.name, args)
        return DispatcherResponse.ToolOutcome(tool.spec.name, result)
    }
}

/** LLM 流式兜底过程中的事件（UI 据此做流式渲染） */
sealed interface LlmStreamEvent {
    /** 模型输出增量文本 */
    data class Delta(val text: String) : LlmStreamEvent

    /** 模型最终答复为纯文本 */
    data class FinalText(val text: String) : LlmStreamEvent

    /** 模型决定调用工具：已注册确认流，UI 出确认卡片 */
    data class ToolCall(val actionId: String, val title: String, val description: String) : LlmStreamEvent

    /** 未配置/网络失败/超时（已自动重试） */
    data class Error(val message: String) : LlmStreamEvent
}

/** 调度结果：UI 根据类型决定渲染气泡还是卡片 */
sealed interface DispatcherResponse {

    /** 纯文本回复 */
    data class Text(val text: String, val source: Source) : DispatcherResponse

    /** 本地工具执行结果（渲染成结果卡片） */
    data class ToolOutcome(val toolName: String, val result: ToolResult) : DispatcherResponse

    /** 需要用户确认的操作（渲染成确认卡片） */
    data class ConfirmRequired(
        val actionId: String,
        val title: String,
        val description: String,
    ) : DispatcherResponse

    /** 开发中占位（只在功能接入前出现，接入即删除） */
    data class Placeholder(val text: String) : DispatcherResponse

    enum class Source {
        /** 本地规则命中 */
        LOCAL_RULE,

        /** 云端大模型文本 */
        LLM,

        /** 云端大模型返回的工具调用 */
        LLM_TOOL,
    }
}
