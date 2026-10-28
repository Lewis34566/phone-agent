package com.lewis.phoneagent.tools.query

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.lewis.phoneagent.core.intent.AppNameResolver
import com.lewis.phoneagent.core.intent.RuleMatcher
import com.lewis.phoneagent.tools.MatchScore
import com.lewis.phoneagent.tools.RankRow
import com.lewis.phoneagent.tools.Tool
import com.lewis.phoneagent.tools.ToolArgs
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.tools.ToolSpec

/**
 * 查询工具③：查看指定应用的权限清单（申请了哪些、哪些已授予）。
 * 无需任何权限（PackageManager 公开接口）。
 * 诚实边界：只到"申请权限"粒度；更细的 AppOps 级别需 Shizuku（预留扩展）。
 */
class AppPermissionsTool(
    private val context: Context,
    private val resolver: AppNameResolver,
) : Tool {

    override val spec = ToolSpec(
        name = "app_permissions",
        description = "查看指定应用申请了哪些权限、哪些已授予",
    )

    override fun match(text: String): MatchScore? {
        if (!text.contains("权限")) return null
        val name = RuleMatcher.extractAppNameFromPermissionQuery(text)
        // 有权限意图但没有应用名 → 低分命中，执行时给出引导
        val score = if (name != null) 0.9f else 0.55f
        return MatchScore(score, mapOf("appName" to name))
    }

    override suspend fun execute(args: ToolArgs): ToolResult {
        val name = args["appName"] as? String
        if (name.isNullOrBlank()) {
            return ToolResult.Text("想查看哪个应用的权限？试试说「查看微信的权限」。")
        }

        val candidates = resolver.resolve(name)
        if (candidates.isEmpty()) {
            return ToolResult.Text("没找到叫「$name」的应用，换个准确的名字试试？")
        }
        if (candidates.size > 1) {
            return ToolResult.Text(
                "「$name」可能指：" + candidates.take(5).joinToString("、") { it.label } +
                    "。请说完整的应用名。",
            )
        }

        val app = candidates.first()
        val pm = context.packageManager
        val pi: PackageInfo = try {
            pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        } catch (e: Exception) {
            return ToolResult.Text("读取「${app.label}」的权限信息失败：${e.message}")
        }

        val requested = pi.requestedPermissions ?: return ToolResult.Text("「${app.label}」没有申请任何权限。")
        val flags = pi.requestedPermissionsFlags ?: IntArray(requested.size)

        val rows = requested.mapIndexed { i, permission ->
            val granted = (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            RankRow(permissionDisplayName(permission), if (granted) "已授予" else "未授予")
        }

        return ToolResult.Ranking(
            title = "「${app.label}」的权限（${rows.size} 项）",
            rows = rows,
            note = "显示该应用申请的权限及授予状态；危险权限（相机/通讯录等）会弹窗询问用户，" +
                "普通权限安装时默认授予。到系统设置页管理权限，说「打开${app.label}的设置」（阶段③接入）。",
        )
    }

    /** 权限名中文化：常见权限映射，未收录的保留英文原名（诚实展示） */
    private fun permissionDisplayName(permission: String): String {
        val base = permission.removePrefix("android.permission.")
        return PERMISSION_NAMES[base] ?: base
    }

    companion object {
        private val PERMISSION_NAMES = mapOf(
            "CAMERA" to "相机",
            "READ_CONTACTS" to "读取通讯录",
            "WRITE_CONTACTS" to "写入通讯录",
            "ACCESS_FINE_LOCATION" to "精确定位",
            "ACCESS_COARSE_LOCATION" to "大致定位",
            "READ_EXTERNAL_STORAGE" to "读取存储",
            "WRITE_EXTERNAL_STORAGE" to "写入存储",
            "READ_MEDIA_IMAGES" to "读取照片",
            "READ_MEDIA_VIDEO" to "读取视频",
            "READ_MEDIA_AUDIO" to "读取音频",
            "RECORD_AUDIO" to "麦克风",
            "READ_PHONE_STATE" to "电话状态",
            "CALL_PHONE" to "拨打电话",
            "READ_SMS" to "读取短信",
            "SEND_SMS" to "发送短信",
            "RECEIVE_SMS" to "接收短信",
            "READ_CALENDAR" to "读取日历",
            "WRITE_CALENDAR" to "写入日历",
            "BLUETOOTH_CONNECT" to "蓝牙连接",
            "BLUETOOTH_SCAN" to "蓝牙扫描",
            "POST_NOTIFICATIONS" to "发送通知",
            "BODY_SENSORS" to "身体传感器",
            "ACTIVITY_RECOGNITION" to "运动识别",
            "SYSTEM_ALERT_WINDOW" to "悬浮窗",
            "FOREGROUND_SERVICE" to "前台服务",
        )
    }
}
