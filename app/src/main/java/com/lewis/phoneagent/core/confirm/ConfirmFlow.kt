package com.lewis.phoneagent.core.confirm

import com.lewis.phoneagent.tools.Tool
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 确认流：管理"待用户确认"的操作。
 *
 * 一切会改变手机状态的操作（needConfirm=true 的工具、LLM 返回的工具调用）
 * 都先在这里挂号，用户在确认卡片上点「确认执行」后才真正跑。
 * 多步任务（LLM 一次返回多个工具调用）作为一组步骤挂同一个号，一次确认。
 *
 * 内存态设计：App 重启后 pending 丢失，此时点旧卡片按钮会得到
 * "操作已失效"的诚实提示，不会误执行。
 */
class ConfirmFlow {

    class Step(val tool: Tool, val args: Map<String, Any?>)

    class PendingAction(val steps: List<Step>)

    private val pending = ConcurrentHashMap<String, PendingAction>()

    /** 挂号一个待确认操作（单步或多步），返回操作 ID */
    fun register(steps: List<Step>): String {
        val id = UUID.randomUUID().toString().substring(0, 8)
        pending[id] = PendingAction(steps)
        return id
    }

    fun register(tool: Tool, args: Map<String, Any?>): String =
        register(listOf(Step(tool, args)))

    /** 取出并移除（确认执行前调用，保证只执行一次） */
    fun take(id: String): PendingAction? = pending.remove(id)

    /** 取消（移除不执行） */
    fun cancel(id: String) {
        pending.remove(id)
    }
}
