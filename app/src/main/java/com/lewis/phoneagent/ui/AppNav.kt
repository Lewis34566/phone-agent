package com.lewis.phoneagent.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lewis.phoneagent.ui.chat.ChatScreen
import com.lewis.phoneagent.ui.game.SnakeGameScreen
import com.lewis.phoneagent.ui.history.HistoryScreen
import com.lewis.phoneagent.ui.settings.SettingsScreen

/**
 * 顶层导航：聊天页 ↔ 历史记录 ↔ 设置页。
 * 历史页选中某会话 → 写入 pendingSession 状态 → 返回聊天页后切换会话。
 */
@Composable
fun AppNav() {
    val navController = rememberNavController()
    var pendingSession by remember { mutableStateOf<Long?>(null) }

    NavHost(navController = navController, startDestination = "chat") {
        composable("chat") {
            ChatScreen(
                onOpenSettings = { navController.navigate("settings") },
                onOpenHistory = { navController.navigate("history") },
                pendingSessionId = pendingSession,
                onSessionConsumed = { pendingSession = null },
            )
        }
        composable("history") {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenSession = { sessionId ->
                    pendingSession = sessionId
                    navController.popBackStack()
                },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
