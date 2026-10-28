package com.lewis.phoneagent.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户自定义规则（数据层先建好，UI 阶段⑤再做「自定义规则」设置项）。
 * 用途：把"用户习惯"落库 —— 例如用户常说的某个说法，绑定到一个工具+参数，
 * 以后本地识别未命中时可以优先查规则表，实现个性化意图。
 */
@Entity(tableName = "user_rule")
data class UserRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 触发文本（用户的原话或关键词） */
    val pattern: String,
    /** 命中的工具名 */
    val toolName: String,
    /** 工具参数 JSON */
    val paramsJson: String,
    /** 是否启用 */
    val enabled: Boolean = true,
    /** 命中次数（以后做规则排序/淘汰的依据） */
    val hitCount: Int = 0,
    val createTime: Long = System.currentTimeMillis(),
)
