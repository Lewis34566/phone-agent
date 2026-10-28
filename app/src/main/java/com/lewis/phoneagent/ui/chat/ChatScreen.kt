package com.lewis.phoneagent.ui.chat

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lewis.phoneagent.PhoneAgentApp
import com.lewis.phoneagent.model.ChatMessage
import com.lewis.phoneagent.model.ContentType
import com.lewis.phoneagent.model.Role

/** 空会话时的欢迎消息（UI 提示，非假历史） */
private const val WELCOME =
    "Hi, I'm your Phone Agent 👋\n" +
    "Try: open WeChat / list installed apps / which apps are unused / take a note…\n" +
    "Chinese commands work too: 打开微信 / 列出已装应用"

/** 聊天主界面 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onLaunchGame: () -> Unit = {},
    pendingSessionId: Long? = null,
    onSessionConsumed: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as PhoneAgentApp
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(app.container))

    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val sending by viewModel.sending.collectAsState()
    val quickCommands by viewModel.quickCommands.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // 搜索结果点击 → 系统浏览器打开
    val openUrl: (String) -> Unit = { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(context, "Cannot open this link", Toast.LENGTH_SHORT).show()
        }
    }

    // 点应用行 → 弹出操作菜单（打开/系统设置/卸载），动作复用现有指令流程（含确认机制）
    var selectedApp by remember { mutableStateOf<String?>(null) }

    // 语音识别：系统识别引擎，无需任何权限；识别结果回填输入框
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) viewModel.onInputChange(spoken)
        }
    }
    val onVoiceClick = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说出你想做的事")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            // 设备无语音引擎：明确提示，不崩溃不静默失败
            Toast.makeText(context, "No speech recognition engine available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    // 拍照：系统相机 → 存私有目录 → 回调 ViewModel（显示图片 + 自动 OCR）
    var currentPhotoPath by remember { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            currentPhotoPath?.let { viewModel.onImageCaptured(it) }
        }
    }
    val onCameraClick = {
        val dir = java.io.File(context.filesDir, "images").apply { mkdirs() }
        val file = java.io.File(dir, "shot_${System.currentTimeMillis()}.jpg")
        currentPhotoPath = file.absolutePath
        val uri: Uri = FileProvider.getUriForFile(
            context, "com.lewis.phoneagent.fileprovider", file,
        )
        try {
            cameraLauncher.launch(uri)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No camera app available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    // 新消息到达时自动滚到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // 从系统授权页返回时：权限已就绪则自动重试之前暂停的指令
    LifecycleResumeEffect(Unit) {
        viewModel.onAppResume()
        onPauseOrDispose { }
    }

    // 历史页选中了某个会话：切过去
    LaunchedEffect(pendingSessionId) {
        pendingSessionId?.let {
            viewModel.switchSession(it)
            onSessionConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phone Agent") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = { viewModel.newSession() }) {
                        Icon(Icons.Default.Add, contentDescription = "New chat")
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            ) {
                // 空会话：欢迎气泡 + 快捷指令
                if (messages.isEmpty()) {
                    item {
                        MessageBubble(
                            ChatMessage(role = Role.ASSISTANT, contentType = ContentType.TEXT, content = WELCOME),
                        )
                    }
                    item {
                        QuickActionsBar(onCommand = viewModel::send, dynamicCommands = quickCommands)
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(
                            message = msg,
                            onConfirm = viewModel::confirm,
                            onCancel = viewModel::cancel,
                            onAppClick = { label -> selectedApp = label },
                            onLaunchGame = onLaunchGame,
                            onOpenUrl = openUrl,
                        )
                    }
                }
                // 发送中指示
                if (sending) {
                    item {
                        Box(Modifier.padding(16.dp)) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterStart))
                        }
                    }
                }
            }

            QuickActionsBar(
                onCommand = viewModel::send,
                dynamicCommands = quickCommands,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            InputBar(
                text = inputText,
                sending = sending,
                onTextChange = viewModel::onInputChange,
                onSend = { viewModel.send(inputText) },
                onVoiceClick = onVoiceClick,
                onCameraClick = onCameraClick,
            )
        }
    }

    // 应用操作菜单
    selectedApp?.let { label ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selectedApp = null },
            title = { Text(label) },
            text = {
                Column {
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.send("打开$label"); selectedApp = null
                    }) { Text("▶ Open app") }
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.send("打开${label}的设置"); selectedApp = null
                    }) { Text("⚙️ System settings (cache / permissions / disable)") }
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.send("卸载$label"); selectedApp = null
                    }) { Text("🗑 Uninstall") }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { selectedApp = null }) { Text("Cancel") }
            },
        )
    }
}
