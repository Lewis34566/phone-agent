package com.lewis.phoneagent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.lewis.phoneagent.data.remind.ReviewReminderScheduler
import com.lewis.phoneagent.ui.AppNav
import com.lewis.phoneagent.ui.theme.PhoneAgentTheme

/**
 * 唯一 Activity：挂载 Compose 界面 + 启动时申请通知权限、预约复习提醒。
 */
class MainActivity : ComponentActivity() {

    /** Android 13+ 通知权限：拒绝不报错，提醒静默跳过，其余功能不受影响 */
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 结果无需处理：有权限提醒照发，没权限静默跳过 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 每次启动都预约：应用被系统杀过闹钟可能丢，重开即恢复
        ReviewReminderScheduler(applicationContext).scheduleNext()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            PhoneAgentTheme {
                AppNav()
            }
        }
    }
}
