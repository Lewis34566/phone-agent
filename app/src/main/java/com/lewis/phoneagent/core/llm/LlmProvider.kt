package com.lewis.phoneagent.core.llm

/**
 * 大模型统一接口 —— 业务逻辑只依赖这个接口，不绑定任何一家 API。
 *
 * 阶段④实现 OpenAiCompatProvider（兼容 OpenAI 格式，可接 DeepSeek 等）；
 * 以后换 Claude、换本地模型，只需新增实现类，调度层零改动。
 */
interface LlmProvider {

    /**
     * 发起一次流式对话。
     *
     * @param messages 对话上下文（只包含与当前问题相关的内容，绝不全量应用列表）
     * @param tools    可用工具描述（JSON Schema），为空表示不允许工具调用
     * @param onDelta  每收到一段增量文本回调一次（用于界面流式渲染）
     * @return 最终结果：纯文本，或模型决定调用的工具 + 参数
     */
    suspend fun streamChat(
        messages: List<LlmMessage>,
        tools: List<ToolSchema>,
        onDelta: suspend (String) -> Unit,
    ): LlmResult
}

/** 一条对话消息（角色 + 内容），与 OpenAI 格式一一对应，转换在 Provider 内部做 */
data class LlmMessage(val role: String, val content: String)

/** 工具描述（给模型看的能力清单，由 ToolSpec 按需转换） */
data class ToolSchema(
    val name: String,
    val description: String,
    val parametersJson: String, // JSON Schema 字符串
) {
    companion object {
        /** 从 ToolSpec 生成 OpenAI 格式的工具描述（提示词按需裁剪：只含 schema，不含数据） */
        fun fromSpec(
            name: String,
            description: String,
            params: List<com.lewis.phoneagent.tools.ParamSpec>,
        ): ToolSchema {
            val props = params.joinToString(",") { p ->
                val type = when (p.type) {
                    com.lewis.phoneagent.tools.ParamType.STRING -> "string"
                    com.lewis.phoneagent.tools.ParamType.INT -> "integer"
                    com.lewis.phoneagent.tools.ParamType.BOOLEAN -> "boolean"
                }
                "\"${p.name}\":{\"type\":\"$type\",\"description\":\"${p.description}\"}"
            }
            val required = params.filter { it.required }.joinToString(",") { "\"${it.name}\"" }
            return ToolSchema(
                name = name,
                description = description,
                parametersJson =
                    "{\"type\":\"object\",\"properties\":{$props},\"required\":[$required]}",
            )
        }
    }
}

/** 模型返回结果：文本 / 单个工具调用 / 多步任务（多工具调用按顺序执行） */
sealed interface LlmResult {
    data class Text(val content: String) : LlmResult
    data class ToolCall(val toolName: String, val args: Map<String, Any?>) : LlmResult
    data class MultiToolCall(val calls: List<ToolCall>) : LlmResult
}

/** Provider 未配置（用户没填 apiKey 等）时抛出，调度层据此给出引导提示 */
class LlmNotConfiguredException : Exception("云端模型未配置，请到设置页填写 API 配置")
