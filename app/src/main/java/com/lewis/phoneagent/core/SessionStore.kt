package com.lewis.phoneagent.core

import android.content.Context
import com.lewis.phoneagent.data.db.MemoryDao
import com.lewis.phoneagent.data.db.MemoryEntity

/**
 * 全局记忆记录器：每次工具执行后把"用户习惯"落库。
 * 记忆内容只在本机，不上传；设置页可查看/删除。
 */
class MemoryRecorder(private val memoryDao: MemoryDao) {

    companion object {
        const val TYPE_APP_USAGE = "app_usage"   // 用户操作过的应用（打开/卸载/查权限）
        const val TYPE_COMMAND = "command"       // 用户说过的指令原话（去应用名后）
        const val TYPE_TOOL = "tool"             // 工具调用频次
    }

    /** 记录一次工具调用：工具名 + 涉及的应用名/关键词 */
    suspend fun record(toolName: String, args: Map<String, Any?>) {
        val now = System.currentTimeMillis()
        bump(TYPE_TOOL, toolName, toolName, 1, now)
        // 从参数里提取应用名/指令关键词，形成用户习惯
        (args["appName"] as? String)?.takeIf { it.isNotBlank() }?.let { name ->
            bump(TYPE_APP_USAGE, name, name, 1, now)
        }
        (args["days"] as? Int)?.let { days ->
            bump(TYPE_COMMAND, "unused_days", "$days", 1, now)
        }
    }

    /** 高频记忆 top N（快捷指令个性化用） */
    suspend fun topFrequentApps(limit: Int): List<MemoryEntity> = memoryDao.top(limit * 3)
        .filter { it.type == TYPE_APP_USAGE }
        .take(limit)

    private suspend fun bump(type: String, key: String, value: String, delta: Int, now: Long) {
        val updated = memoryDao.bump(type, key, value, delta, now)
        if (updated == 0) memoryDao.insert(MemoryEntity(type = type, key = key, value = value, count = delta, updateTime = now))
    }
}

/** 当前会话 id 的持久化（轻量 SharedPreferences，不引 DataStore 依赖） */
class SessionStore(context: Context) {

    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun current(): Long = prefs.getLong("current_session", 0L)

    /** 开新会话：返回新 sessionId */
    fun newSession(): Long {
        val id = System.currentTimeMillis()
        prefs.edit().putLong("current_session", id).apply()
        return id
    }

    fun switchTo(sessionId: Long) {
        prefs.edit().putLong("current_session", sessionId).apply()
    }
}
