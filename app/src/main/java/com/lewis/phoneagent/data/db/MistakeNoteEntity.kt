package com.lewis.phoneagent.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 错题本（v1 核心场景）：拍照 → OCR → 存入本表 → 按遗忘曲线提醒复习。
 *
 * stage 含义：0=新存入（明天复习）1..5=已复习次数（1/3/7/15/30 天间隔，见 MistakeRepository）。
 * 复习答对 stage+1，答错归 0 重新走曲线。
 * 只存本机；题目/答案/学科/图片路径（应用私有目录）。
 */
@Entity(tableName = "mistake_note")
data class MistakeNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val question: String,
    val answer: String = "",
    val subject: String = "",
    val imagePath: String = "",
    val stage: Int = 0,
    val nextReviewAt: Long = 0,
    val wrongCount: Int = 0,
    val createTime: Long = System.currentTimeMillis(),
)
