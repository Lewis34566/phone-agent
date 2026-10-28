package com.lewis.phoneagent.model

import kotlinx.serialization.Serializable

/**
 * 应用信息模型（AppRepository 填充）。
 *
 * 字段说明（诚实边界）：
 *  - apkSizeBytes：APK 文件大小，无需任何权限
 *  - totalSizeBytes：完整占用（代码+数据+缓存），需「使用情况访问」授权，未授权为 null
 *  - lastUsedTimeMs：最近使用时间，需「使用情况访问」授权，从未使用过/未授权为 null
 */
@Serializable
data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val installTimeMs: Long,
    val lastUpdateTimeMs: Long,
    val isSystem: Boolean,
    val apkSizeBytes: Long?,
    val totalSizeBytes: Long? = null,
    val lastUsedTimeMs: Long? = null,
)
