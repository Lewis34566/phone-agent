package com.lewis.phoneagent.tools.privileged

/**
 * Shizuku 桥接占位 —— 预留，不实现。
 *
 * 用途（未来）：通过 Shizuku 的 binder 通道以 shell 权限调用系统 API，
 * 解锁真实耗电统计、自动清缓存等特权能力。
 *
 * 实现时参考：Shizuku API（rikka.shizuku）授权流程
 *  - 检测 Shizuku 是否安装并已授权本 App
 *  - 通过 ShizukuBinderWrapper 调用隐藏系统服务
 *
 * 当前版本不引入 Shizuku 依赖，避免增加安装门槛——先保证纯本地 MVP 可用。
 */
object ShizukuBridge {
    const val NOT_IMPLEMENTED =
        "Shizuku 扩展未实现：需要用户手机安装 Shizuku 并授权。当前版本的对应功能" +
            "以系统界面跳转代替（诚实方案）。"
}
