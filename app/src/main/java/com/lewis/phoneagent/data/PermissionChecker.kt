package com.lewis.phoneagent.data

import android.app.AppOpsManager
import android.content.Context
import android.os.Process

/**
 * 权限检测中心：一切需要特殊授权的功能先在这里检测。
 * 未授权 → 工具返回 NeedPermission 结果 → UI 出引导卡片（不崩溃、不静默失败）。
 */
class PermissionChecker(context: Context) {

    private val appContext = context.applicationContext

    /**
     * 「使用情况访问」权限是否已授权。
     * 三个功能依赖它：长期未用应用、完整占用空间、后台活动排行。
     */
    fun hasUsageAccess(): Boolean {
        val appOps = appContext.getSystemService(AppOpsManager::class.java)
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            appContext.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
