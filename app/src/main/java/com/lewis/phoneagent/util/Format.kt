package com.lewis.phoneagent.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 展示格式化工具：字节大小 / 时间 / 时长 */
object Format {

    fun bytes(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(Locale.CHINA, "%.1f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(Locale.CHINA, "%.1f MB", bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> String.format(Locale.CHINA, "%.0f KB", bytes / (1L shl 10).toDouble())
        else -> "$bytes B"
    }

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    fun date(ms: Long): String = dateFmt.format(Date(ms))

    /** 时长：如 "3天5小时"、"2小时10分"、"5分钟" */
    fun duration(ms: Long): String {
        val minutes = ms / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 -> "${days}天${hours % 24}小时"
            hours > 0 -> "${hours}小时${minutes % 60}分"
            minutes > 0 -> "${minutes}分钟"
            else -> "不足1分钟"
        }
    }

    /** 相对时间：如 "3天前"、"2个月前" */
    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        val diffDays = (now - ms) / (24L * 3600_000)
        return when {
            diffDays <= 0 -> "今天"
            diffDays == 1L -> "昨天"
            diffDays < 30 -> "${diffDays}天前"
            diffDays < 365 -> "${diffDays / 30}个月前"
            else -> "${diffDays / 365}年前"
        }
    }
}
