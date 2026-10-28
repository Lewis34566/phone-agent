package com.lewis.phoneagent.core

import com.lewis.phoneagent.core.confirm.ConfirmFlow
import com.lewis.phoneagent.core.llm.LlmMessage
import com.lewis.phoneagent.core.llm.LlmNotConfiguredException
import com.lewis.phoneagent.core.llm.LlmProvider
import com.lewis.phoneagent.core.llm.LlmResult
import com.lewis.phoneagent.core.llm.ToolSchema
import com.lewis.phoneagent.model.ChatMessage
import com.lewis.phoneagent.model.ContentType
import com.lewis.phoneagent.model.Role
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolRegistry
import com.lewis.phoneagent.tools.ToolResult

/**
 * 调度层核心：输入文本 → 本地意图识别 → 工具执行；未命中 → LlmProvider 兜底。
 *
 * 流程：
 *   1. 遍历注册表让每个工具自带 match() 打分，取最高分
 *   2. 分数 ≥ 阈值 → needConfirm 工具先出确认卡片【阶段③】；
 *      其余直接执行【阶段②】
 *   3. 全部未命中 → LlmProvider 流式兜底（文本直接显示 / 工具调用→一律确认→执行）【阶段④】
 */
class Dispatcher(
    private val registry: ToolRegistry,
    private val confirmFlow: ConfirmFlow = ConfirmFlow(),
    private val llmProvider: LlmProvider? = null,
    private val memoryRecorder: MemoryRecorder? = null,
) {

    /** 本地规则命中的最低分阈值（0~1），低于此分认为没听懂，走 LLM 兜底 */
    companion object {
        const val MATCH_THRESHOLD = 0.6f

        private const val SYSTEM_PROMPT =
            "你是安卓手机管家助手。能力包括：列出应用、找长期未用应用、查看应用权限、后台活动排行、" +
            "存储空间、打开应用、卸载应用、跳转应用设置页、记便签、查便签。" +
            "用户需求对应某个能力时，输出工具调用（工具名+参数 JSON）；" +
            "一句话包含多个需求（如'列应用再查存储'）时，一次返回多个工具调用（按顺序）。" +
            "无法确定时正常用简体中文简短回复，不要编造能力。写作/总结/翻译类请求直接文本回答。"
    }

    /** 处理一条用户输入，返回给 UI 的响应 */
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
        // 本地未命中：交给 ViewModel 走 LLM 流式兜底（processLlmFallback）
        return DispatcherResponse.Placeholder(text)
    }

    /**
     * LLM 流式兜底：本地全部未命中时调用。
     * 事件流：
     *  - Delta     ：模型输出增量文本（UI 流式渲染）
     *  - FinalText ：模型最终答复为纯文本
     *  - ToolCall  ：模型决定调用工具 → 已注册确认流，UI 出确认卡片
     *  - Error     ：未配置/网络失败/超时（已自动重试 2 次）→ UI 出错误提示
     */
    suspend fun processLlmFallback(
        text: String,
        history: List<ChatMessage>,
        onEvent: suspend (LlmStreamEvent) -> Unit,
    ) {
        val provider = llmProvider
        if (provider == null) {
            onEvent(LlmStreamEvent.Error(
                "云端模型未配置。去右上角设置页填 baseUrl / apiKey / model（默认 DeepSeek）后，" +
                    "本地认不出的模糊需求就会交给模型处理。本地指令不受影响。",
            ))
            return
        }

        // 提示词裁剪：只发 system 引导 + 最近 4 轮文本 + 当前问题，绝不塞应用列表
        val messages = buildLlmMessages(text, history)
        val schemas = registry.all().map {
            ToolSchema.fromSpec(it.spec.name, it.spec.description, it.spec.params)
        }

        try {
            val result = provider.streamChat(messages, schemas) { delta -> onEvent(LlmStreamEvent.Delta(delta)) }
            when (result) {
                is LlmResult.Text -> onEvent(LlmStreamEvent.FinalText(result.content))
                is LlmResult.ToolCall -> registerToolConfirm(listOf(result), onEvent)
                is LlmResult.MultiToolCall -> registerToolConfirm(result.calls, onEvent)
            }
        } catch (e: LlmNotConfiguredException) {
            onEvent(LlmStreamEvent.Error("云端模型未配置：去设置页填写 baseUrl / apiKey / model。"))
        } catch (e: Exception) {
            onEvent(LlmStreamEvent.Error(
                "云端模型调用失败：${e.message ?: e.javaClass.simpleName}（已重试）。检查网络或设置页配置。",
            ))
        }
    }

    /**
     * 模型返回的工具调用（单个或多个）→ 注册确认流。
     * 模型可能误解用户意图，一律先出确认卡片；多步任务合并成一张计划卡。
     */
    private suspend fun registerToolConfirm(
        calls: List<LlmResult.ToolCall>,
        onEvent: suspend (LlmStreamEvent) -> Unit,
    ) {
        val steps = calls.mapNotNull { call ->
            val tool = registry.byName(call.toolName)
            if (tool == null) {
                onEvent(LlmStreamEvent.FinalText("模型想调用未知工具「${call.toolName}」，已忽略。换个说法再试试。"))
                return@mapNotNull null
            }
            com.lewis.phoneagent.core.confirm.ConfirmFlow.Step(tool, call.args)
        }
        if (steps.isEmpty()) return
        val actionId = confirmFlow.register(steps)
        if (steps.size == 1) {
            val (title, description) = steps[0].tool.confirmPrompt(steps[0].args)
            onEvent(LlmStreamEvent.ToolCall(actionId, title, description))
        } else {
            val plan = steps.joinToString("\n") { s ->
                val (t, _) = s.tool.confirmPrompt(s.args)
                "• $t"
            }
            onEvent(LlmStreamEvent.ToolCall(
                actionId,
                "执行 ${steps.size} 步任务？",
                plan + "\n\n每步执行完会汇总结果。",
            ))
        }
    }

    /** 构建 LLM 消息：system + 最近 4 轮 TEXT 历史 + 当前输入（提示词按需裁剪） */
    private fun buildLlmMessages(text: String, history: List<ChatMessage>): List<LlmMessage> {
        val recent = history
            .filter { it.contentType == ContentType.TEXT }
            .takeLast(4)
            .map { LlmMessage(if (it.role == Role.USER) "user" else "assistant", it.content) }
        return listOf(LlmMessage("system", SYSTEM_PROMPT)) + recent + LlmMessage("user", text)
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
