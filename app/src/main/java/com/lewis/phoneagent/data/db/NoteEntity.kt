package com.lewis.phoneagent.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 便签（办公能力①）：用户说"记一下XX"时保存。
 * 只存本机；"我的便签"可查，"删除便签XX"可删。
 */
@Entity(tableName = "note")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val createTime: Long = System.currentTimeMillis(),
)
