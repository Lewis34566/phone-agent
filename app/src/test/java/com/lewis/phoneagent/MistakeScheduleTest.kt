package com.lewis.phoneagent

import com.lewis.phoneagent.data.MistakeRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 遗忘曲线纯函数单测：答对晋级、答错归零、间隔天数。
 * 曲线规则是错题本的核心承诺，改规则必须过这里。
 */
class MistakeScheduleTest {

    @Test
    fun `答对逐级晋级`() {
        assertEquals(1, MistakeRepository.nextStageAfterReview(0, correct = true))
        assertEquals(2, MistakeRepository.nextStageAfterReview(1, correct = true))
        assertEquals(3, MistakeRepository.nextStageAfterReview(2, correct = true))
        assertEquals(4, MistakeRepository.nextStageAfterReview(3, correct = true))
        assertEquals(5, MistakeRepository.nextStageAfterReview(4, correct = true))
    }

    @Test
    fun `答对封顶不再升`() {
        assertEquals(5, MistakeRepository.nextStageAfterReview(5, correct = true))
    }

    @Test
    fun `答错归零重新走曲线`() {
        assertEquals(0, MistakeRepository.nextStageAfterReview(3, correct = false))
        assertEquals(0, MistakeRepository.nextStageAfterReview(5, correct = false))
        assertEquals(0, MistakeRepository.nextStageAfterReview(0, correct = false))
    }

    @Test
    fun `间隔天数 1_3_7_15_30_30`() {
        assertEquals(1, MistakeRepository.daysUntilNextReview(0))
        assertEquals(3, MistakeRepository.daysUntilNextReview(1))
        assertEquals(7, MistakeRepository.daysUntilNextReview(2))
        assertEquals(15, MistakeRepository.daysUntilNextReview(3))
        assertEquals(30, MistakeRepository.daysUntilNextReview(4))
        assertEquals(30, MistakeRepository.daysUntilNextReview(5))
    }

    @Test
    fun `越界 stage 被夹住不崩溃`() {
        assertEquals(30, MistakeRepository.daysUntilNextReview(99))
        assertEquals(1, MistakeRepository.daysUntilNextReview(-1))
    }
}
