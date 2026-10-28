package com.lewis.phoneagent.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.lewis.phoneagent.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * 应用列表仓库：后台协程扫描 + 内存缓存。
 *
 * 性能设计：
 *  - 首次查询触发一次 PackageManager 扫描（binder 调用），在 IO 线程执行
 *  - 结果缓存 TTL 5 分钟，二次查询直接读缓存零开销
 *  - matcher 需要快速取候选应用名时用 [cachedOrNull]（同步、无挂起）
 *
 * 口径：
 *  - includeAll=false（默认）：只列有桌面启动图标的应用
 *  - includeAll=true：全部应用（含纯后台/系统组件）——"管理所有软件"用
 */
class AppRepository(private val context: Context) {

    private val pm = context.packageManager

    /** 缓存：全量列表 + 可启动包名集合（内存过滤，不重新扫描） */
    private class Cached(val all: List<AppInfo>, val launchable: Set<String>, val at: Long)

    private val cache = AtomicReference<Cached?>()

    companion object {
        /** 缓存有效期：5 分钟 */
        const val TTL_MS = 5 * 60 * 1000L
    }

    /** 同步快读（默认口径）：缓存有效直接返回，否则 null（供本地意图匹配快速取候选名） */
    fun cachedOrNull(): List<AppInfo>? {
        val c = cache.get() ?: return null
        if (System.currentTimeMillis() - c.at >= TTL_MS) return null
        return c.all.filter { it.packageName in c.launchable }
    }

    /**
     * 应用列表（默认走缓存；forceRefresh=true 强制重扫，如用户刚装/卸了应用）。
     */
    suspend fun apps(forceRefresh: Boolean = false, includeAll: Boolean = false): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val c = cache.get()
            if (!forceRefresh && c != null && System.currentTimeMillis() - c.at < TTL_MS) {
                return@withContext if (includeAll) c.all else c.all.filter { it.packageName in c.launchable }
            }

            // 一次查询拿到所有「可启动」应用的包名集合（有 LAUNCHER 入口）
            val launchable = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_ALL,
            ).mapTo(HashSet()) { it.activityInfo.packageName }

            // 一次 binder 调用拿全部包信息（版本/安装时间/源码路径）
            val all = pm.getInstalledPackages(0).mapNotNull { pi ->
                val ai = pi.applicationInfo ?: return@mapNotNull null
                AppInfo(
                    packageName = pi.packageName,
                    label = ai.loadLabel(pm).toString(),
                    versionName = pi.versionName,
                    installTimeMs = pi.firstInstallTime,
                    lastUpdateTimeMs = pi.lastUpdateTime,
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    // APK 文件大小：无需任何权限，从源码路径取
                    apkSizeBytes = ai.sourceDir
                        ?.takeIf { it.isNotBlank() }
                        ?.let { File(it).length() }
                        ?.takeIf { it > 0 },
                )
            }.sortedByDescending { it.installTimeMs }

            cache.set(Cached(all, launchable, System.currentTimeMillis()))
            if (includeAll) all else all.filter { it.packageName in launchable }
        }
}
