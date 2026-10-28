package com.lewis.phoneagent.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 全局记忆（Agent 的"总的 memory"）：跨会话保留的用户习惯与事实。
 * 每次工具执行后由 MemoryRecorder 增量记录；Dispatcher 用它做个性化
 * （快捷指令置顶、模糊意图兜底），设置页可查看/删除。
 *
 * 例：type=app_usage, key=微信, value=5（用户打开过微信 5 次）
 */
@Entity(tableName = "memory")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val key: String,
    val value: String,
    val count: Int = 1,
    val updateTime: Long = System.currentTimeMillis(),
)
