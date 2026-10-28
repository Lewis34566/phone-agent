package com.lewis.phoneagent.data

import android.app.usage.StorageStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 系统「使用情况访问」数据仓库：最后使用时间、前台时长、后台时长、完整占用空间。
 * 所有方法内部先做授权检查，未授权返回空/ null（由工具层负责出引导卡片）。
 */
class UsageRepository(private val context: Context) {

    private val checker = PermissionChecker(context)
    private val usm = context.getSystemService(UsageStatsManager::class.java)

    /** 单个包的使用情况摘要 */
    data class PackageUsage(
        var lastUsed: Long = 0L,
        var foregroundMs: Long = 0L,
    )

    /** 每个包最近 days 天内的：最后使用时间 + 前台总时长。从未使用过的包不在结果里。 */
    suspend fun stats(days: Int): Map<String, PackageUsage> = withContext(Dispatchers.IO) {
        if (!checker.hasUsageAccess()) return@withContext emptyMap()
        val end = System.currentTimeMillis()
        val begin = end - days * DAY_MS
        val result = HashMap<String, PackageUsage>()
        try {
            for (s in usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, end)) {
                val e = result.getOrPut(s.packageName) { PackageUsage() }
                if (s.lastTimeUsed > e.lastUsed) e.lastUsed = s.lastTimeUsed
                e.foregroundMs += s.totalTimeInForeground
            }
        } catch (_: Exception) {
            // 个别 ROM 上 queryUsageStats 抛异常时返回已收集部分
        }
        result
    }

    /**
     * 每个包最近 days 天内的后台活动时长（毫秒），从 UsageEvents 推算：
     * 应用进入后台（MOVE_TO_BACKGROUND/ACTIVITY_STOPPED）到回到前台之间的时间累加。
     * 这是近似估算，不是系统电量统计——口径在工具结果的 note 里向用户说明。
     */
    suspend fun backgroundDurationMs(days: Int): Map<String, Long> = withContext(Dispatchers.IO) {
        if (!checker.hasUsageAccess()) return@withContext emptyMap()
        val end = System.currentTimeMillis()
        val begin = end - days * DAY_MS
        val events = try {
            usm.queryEvents(begin, end)
        } catch (_: Exception) {
            return@withContext emptyMap()
        }
        val bgStart = HashMap<String, Long>()   // 包名 -> 进入后台的时间
        val bgTotal = HashMap<String, Long>()   // 包名 -> 后台时长累计
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_STOPPED,
                UsageEvents.Event.MOVE_TO_BACKGROUND ->
                    if (bgStart[e.packageName] == null) bgStart[e.packageName] = e.timeStamp

                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    val start = bgStart.remove(e.packageName) ?: continue
                    val dur = e.timeStamp - start
                    if (dur > 0) bgTotal[e.packageName] = (bgTotal[e.packageName] ?: 0L) + dur
                }
            }
        }
        // 窗口结束仍停留在后台的应用：记到窗口末尾
        for ((pkg, start) in bgStart) {
            val dur = end - start
            if (dur > 0) bgTotal[pkg] = (bgTotal[pkg] ?: 0L) + dur
        }
        bgTotal
    }

    /**
     * 完整占用空间（代码+数据+缓存）。需「使用情况访问」授权；
     * 未授权或查询失败返回 null（工具层降级为 APK 大小并说明）。
     */
    suspend fun totalSizeBytes(packageName: String): Long? = withContext(Dispatchers.IO) {
        if (!checker.hasUsageAccess()) return@withContext null
        try {
            val sm = context.getSystemService(StorageStatsManager::class.java)
            val stats = sm.queryStatsForPackage(
                StorageManager.UUID_DEFAULT,
                packageName,
                Process.myUserHandle(),
            )
            stats.appBytes + stats.dataBytes + stats.cacheBytes
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val DAY_MS = 24L * 3600_000
    }
}
