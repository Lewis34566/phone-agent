package com.lewis.phoneagent.tools

/**
 * 工具注册表：Dispatcher 只认识注册表，不认识任何具体工具。
 * 新增工具只需在 AppContainer 的构造列表里加一项，这里和调度层都不用改。
 */
class ToolRegistry(private val tools: List<Tool>) {

    /** 所有工具（给 LLM 生成工具清单用） */
    fun all(): List<Tool> = tools

    /** 按名称查找（LLM 返回工具调用时校验用） */
    fun byName(name: String): Tool? = tools.firstOrNull { it.spec.name == name }

    /**
     * 让每个工具对输入文本打分，返回最高分者。
     * 返回 null 表示所有工具都认为不匹配。
     */
    fun matchBest(text: String): BestMatch? {
        var best: BestMatch? = null
        for (tool in tools) {
            val match = tool.match(text) ?: continue
            if (match.score > (best?.score ?: 0f)) {
                best = BestMatch(tool, match.score, match.args)
            }
        }
        return best
    }
}

data class BestMatch(val tool: Tool, val score: Float, val args: ToolArgs)
