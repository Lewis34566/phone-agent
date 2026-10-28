package com.lewis.phoneagent

import android.app.Application
import com.lewis.phoneagent.core.AppContainer

/**
 * Application 入口。
 *
 * 冷启动 <1s 的关键：onCreate 里不做任何重初始化（不建库、不扫应用列表），
 * 全部依赖通过 AppContainer 懒加载，首次真正用到时才创建。
 */
class PhoneAgentApp : Application() {

    /** 手动依赖注入容器（by lazy = 首次访问才初始化） */
    val container: AppContainer by lazy { AppContainer(this) }
}
