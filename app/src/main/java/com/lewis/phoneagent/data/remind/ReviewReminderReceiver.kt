package com.lewis.phoneagent.data.remind

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lewis.phoneagent.MainActivity
import com.lewis.phoneagent.data.db.AppDatabase
import kotlinx.coroutines.runBlocking

/**
 * 复习提醒接收器：闹钟到点 → 查错题本到期数 → 有题发通知，并预约下一次。
 *
 * 诚实边界：
 *  - Android 13+ 需要通知权限（POST_NOTIFICATIONS，MainActivity 启动时申请），
 *    未授权时静默跳过（不发通知也不崩），错题数据不受影响；
 *  - 应用被用户强行停止后系统会取消其闹钟，重新打开 App 时会自动再预约。
 */
class ReviewReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val db = AppDatabase.create(context)
        val scheduler = ReviewReminderScheduler(context)
        // 测试钩子恢复：一次性调试延迟发完即回到正常节奏
        scheduler.debugDelayMs = null
        try {
            val due = runBlocking { db.mistakeNoteDao().dueCount(System.currentTimeMillis()) }
            if (due > 0 && ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, buildNotification(context, due))
            }
        } finally {
            scheduler.scheduleNext() // 无论有没有题都预约下一次
            db.close()
        }
    }

    private fun buildNotification(context: Context, due: Int): android.app.Notification {
        createChannel(context)
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("📚 Mistake review")
            .setContentText(if (due == 1) "1 mistake due for review today" else "$due mistakes due for review today")
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Mistake review reminder", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    companion object {
        private const val CHANNEL_ID = "mistake_review_reminder"
        private const val NOTIFICATION_ID = 1001
    }
}
