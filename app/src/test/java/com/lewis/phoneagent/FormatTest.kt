package com.lewis.phoneagent

import com.lewis.phoneagent.util.Format
import org.junit.Assert.assertEquals
import org.junit.Test

/** 展示格式化单元测试 */
class FormatTest {

    @Test
    fun `字节格式化`() {
        assertEquals("1.5 GB", Format.bytes((1.5 * 1024 * 1024 * 1024).toLong()))
        assertEquals("2.0 MB", Format.bytes(2L * 1024 * 1024))
        assertEquals("512 KB", Format.bytes(512L * 1024))
        assertEquals("100 B", Format.bytes(100L))
    }

    @Test
    fun `时长格式化`() {
        assertEquals("3天5小时", Format.duration(3L * 24 * 3600_000 + 5L * 3600_000))
        assertEquals("2小时10分", Format.duration(2L * 3600_000 + 10 * 60_000))
        assertEquals("5分钟", Format.duration(5 * 60_000L))
    }

    @Test
    fun `相对时间`() {
        val now = 1_000_000_000_000L
        assertEquals("今天", Format.ago(now, now))
        assertEquals("昨天", Format.ago(now - 24L * 3600_000, now))
        assertEquals("3天前", Format.ago(now - 3 * 24L * 3600_000, now))
        assertEquals("2个月前", Format.ago(now - 60 * 24L * 3600_000, now))
    }
}
