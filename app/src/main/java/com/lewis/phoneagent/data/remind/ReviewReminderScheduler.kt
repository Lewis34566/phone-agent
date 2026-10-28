package com.lewis.phoneagent.data.remind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * 错题本复习提醒调度：每天 09:00 响一次（不精确闹钟，无需特殊权限）。
 * 到点后 [ReviewReminderReceiver] 查到期题数，有题才发通知，并预约下一次。
 *
 * 为什么用 setAndAllowWhileIdle 而不是 setExact：
 *  - 精确闹钟需要 SCHEDULE_EXACT_ALARM 权限（Android 14 起新装 App 默认拒绝），
 *    提醒"早上9点复习"差几分钟完全无所谓，不值得向用户要危险权限。
 *  - 非精确闹钟在 Doze 模式下由系统批量唤醒，省电且免权限。
 */
class ReviewReminderScheduler(private val context: Context) {

    /** 测试钩子：非 null 时下次提醒改为 now+毫秒数（验证通知链路用），发完自动恢复 */
    var debugDelayMs: Long? = null

    /** 预约下一次提醒：默认明天 09:00（已过点就今天 09:00 顺延到明天） */
    fun scheduleNext() {
        val now = System.currentTimeMillis()
        val at = debugDelayMs?.let { now + it }
            ?: nextNineAm(now)
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent())
    }

    fun cancel() {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent())
    }

    /** 下一个 09:00（今天已过则明天） */
    private fun nextNineAm(now: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, ReviewReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val REQUEST_CODE = 0
    }
}
