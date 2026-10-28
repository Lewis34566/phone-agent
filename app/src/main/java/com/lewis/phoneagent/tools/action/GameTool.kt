package com.lewis.phoneagent.tools.action

import com.lewis.phoneagent.core.intent.RuleMatcher
import com.lewis.phoneagent.tools.MatchScore
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolArgs
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.tools.ToolSpec

/**
 * 小游戏工具：说「玩贪吃蛇」→ 回复带「启动游戏」按钮的卡片 → 进入游戏界面。
 * 游戏是 App 内置的本地实现（Canvas 手写，零依赖、离线可玩）。
 * 诚实边界：这不是 LLM 实时写代码生成的游戏——实时生成可运行代码需要代码沙箱，超出本 App 范围。
 */
class GameTool : Tool {

    override val spec = ToolSpec(
        name = "play_game",
        description = "打开内置小游戏（贪吃蛇）",
    )

    override fun match(text: String): MatchScore? {
        val score = RuleMatcher.keywordScore(
            text,
            strong = listOf("贪吃蛇", "玩游戏", "小游戏", "做个游戏", "来一局"),
        ) ?: return null
        return MatchScore(score, mapOf("game" to "snake"))
    }

    override suspend fun execute(args: ToolArgs): ToolResult =
        ToolResult.Game(
            title = "贪吃蛇",
            note = "滑动控制方向，吃食物得分。这是 App 内置的本地游戏，离线可玩。",
        )
}
