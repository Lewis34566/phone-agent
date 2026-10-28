package com.lewis.phoneagent.tools.privileged

import com.lewis.phoneagent.tools.Tool

/**
 * 特权工具扩展接口 —— 预留，不实现。
 *
 * 以下能力普通 App 永远做不到（Android 安全设计），需要系统级权限：
 *  - 真实耗电排行（BATTERY_STATS 系统权限）
 *  - 自动清理其他应用的缓存 / 强制停止
 *  - 静默卸载（无系统确认界面）
 *  - AppOps 细粒度权限管理
 *  - 自动化点击/脚本（无障碍服务）
 *
 * 可选扩展路线（二选一，都需要用户主动安装/开启）：
 *  1. Shizuku（用户手机装 Shizuku App 并授权，App 通过 binder 调用系统 API）
 *  2. 无障碍服务（用户手动开启，App 模拟点击系统界面）
 *
 * 未来实现时：新增类实现本接口 + Tool 接口，在 AppContainer 注册即可，
 * 调度层和 UI 层零改动。
 */
interface PrivilegedTool : Tool {
    /** 特权通道是否可用（Shizuku 已授权 / 无障碍已开启） */
    fun available(): Boolean
}
