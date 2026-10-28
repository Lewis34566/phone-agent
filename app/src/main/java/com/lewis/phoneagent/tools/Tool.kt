package com.lewis.phoneagent.tools

/**
 * 工具统一契约 —— 整个 App 的解耦根基。
 *
 * 每个工具自带三样东西：
 *   1. spec  ：自我介绍（给调度层和 LLM 看）
 *   2. match ：本地意图匹配（让常见指令不经过任何大模型，<300ms 内响应）
 *   3. execute：真正干活的地方
 *
 * 新增工具 = 新建一个类实现本接口 + 在 AppContainer 注册一行，调度层零改动。
 */
interface Tool {

    val spec: ToolSpec

    /**
     * 本地意图匹配：判断输入文本是否想调用本工具。
     * 返回 null 表示不匹配；返回 [MatchScore] 表示匹配，score 越高越确信。
     * 匹配所需的依赖（如应用列表缓存）通过构造器注入。
     */
    fun match(text: String): MatchScore?

    /**
     * 执行工具。所有重活（IO、系统调用）内部自己切协程线程。
     */
    suspend fun execute(args: ToolArgs): ToolResult

    /**
     * 确认卡片的标题与说明（needConfirm=true 时使用）。
     * 默认通用文案；会改变手机状态的工具应覆写为具体说明（如卸载后果）。
     */
    fun confirmPrompt(args: ToolArgs): Pair<String, String> =
        "执行「${spec.name}」" to "该操作会改变手机状态，是否继续？"
}

/** 工具的自描述：名称、用途、参数 schema、是否需要用户确认 */
data class ToolSpec(
    val name: String,
    val description: String,        // 给 LLM 看的一句话能力描述
    val params: List<ParamSpec> = emptyList(),
    val needConfirm: Boolean = false, // true = 执行前必须弹确认卡片（一切改变手机状态的操作）
)

data class ParamSpec(
    val name: String,
    val type: ParamType,
    val required: Boolean,
    val description: String,
)

enum class ParamType { STRING, INT, BOOLEAN }

/** 本地匹配结果：置信度 + 解析出的参数 */
data class MatchScore(val score: Float, val args: ToolArgs = emptyMap())

typealias ToolArgs = Map<String, Any?>
