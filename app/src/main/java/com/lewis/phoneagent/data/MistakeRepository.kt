package com.lewis.phoneagent.data

import com.lewis.phoneagent.data.db.MistakeNoteDao
import com.lewis.phoneagent.data.db.MistakeNoteEntity
import java.util.concurrent.TimeUnit

/**
 * 错题本仓储：遗忘曲线规则 + 数据读写。
 *
 * 间隔表（天）：1 / 3 / 7 / 15 / 30 / 30，下标 = 复习阶段 stage。
 *  - 新题 stage=0：明天复习
 *  - 答对：stage+1（封顶 5），按新 stage 定下次间隔
 *  - 答错：stage 归 0，重新从 1 天走起
 *
 * 曲线规则是纯函数（companion），不依赖 Android，可直接单元测试。
 */
class MistakeRepository(private val dao: MistakeNoteDao) {

    suspend fun add(question: String, answer: String, subject: String, imagePath: String = ""): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            MistakeNoteEntity(
                question = question,
                answer = answer,
                subject = subject.ifBlank { "未分类" },
                imagePath = imagePath,
                stage = 0,
                nextReviewAt = now + TimeUnit.DAYS.toMillis(INTERVALS_DAYS[0].toLong()),
                createTime = now,
            ),
        )
    }

    /** 记录一次复习结果，返回更新后的题（id 不存在返回 null） */
    suspend fun recordReview(id: Long, correct: Boolean): MistakeNoteEntity? {
        val note = dao.byId(id) ?: return null
        val newStage = nextStageAfterReview(note.stage, correct)
        dao.updateReview(
            id = id,
            stage = newStage,
            next = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(daysUntilNextReview(newStage).toLong()),
            wrongDelta = if (correct) 0 else 1,
        )
        return dao.byId(id)
    }

    suspend fun due(limit: Int = 20): List<MistakeNoteEntity> =
        dao.due(System.currentTimeMillis(), limit)

    suspend fun dueCount(): Int = dao.dueCount(System.currentTimeMillis())

    suspend fun total(): Int = dao.total()

    suspend fun search(keyword: String, limit: Int = 20): List<MistakeNoteEntity> =
        dao.search(keyword, limit)

    suspend fun recent(limit: Int = 20): List<MistakeNoteEntity> = dao.recent(limit)

    companion object {
        /** 遗忘曲线间隔（天），下标 = stage */
        val INTERVALS_DAYS = intArrayOf(1, 3, 7, 15, 30, 30)
        const val MAX_STAGE = 5

        /** 答对 stage+1（封顶），答错归 0 —— 纯函数，单测覆盖 */
        fun nextStageAfterReview(stage: Int, correct: Boolean): Int =
            if (correct) (stage + 1).coerceAtMost(MAX_STAGE) else 0

        /** 某阶段下次复习要隔多少天 —— 纯函数，单测覆盖 */
        fun daysUntilNextReview(stage: Int): Int =
            INTERVALS_DAYS[stage.coerceIn(0, MAX_STAGE)]
    }
}
