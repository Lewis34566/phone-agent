package com.lewis.phoneagent.ui.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Quick command bar: frequent local commands as one-tap buttons.
 * The sent text is matched locally without any cloud model.
 */
private val QUICK_COMMANDS = listOf(
    "📦" to "列出已装应用",
    "🧹" to "哪些应用很久没用了",
    "🔐" to "查看微信的权限",
    "⚡" to "哪些应用后台活动多",
)

@Composable
fun QuickActionsBar(
    onCommand: (String) -> Unit,
    dynamicCommands: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QUICK_COMMANDS.forEach { (emoji, label) ->
            AssistChip(
                onClick = { onCommand(label) },
                label = { Text("$emoji $label") },
            )
        }
        // Dynamic commands: frequently-used apps from global memory, adapts to habits
        dynamicCommands.forEach { label ->
            AssistChip(
                onClick = { onCommand(label) },
                label = { Text("💡 $label") },
            )
        }
    }
}
