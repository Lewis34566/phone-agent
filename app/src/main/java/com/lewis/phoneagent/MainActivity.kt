package com.lewis.phoneagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lewis.phoneagent.ui.AppNav
import com.lewis.phoneagent.ui.theme.PhoneAgentTheme

/**
 * 唯一 Activity：只负责挂载 Compose 界面，不含业务逻辑。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhoneAgentTheme {
                AppNav()
            }
        }
    }
}
