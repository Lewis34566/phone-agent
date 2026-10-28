package com.lewis.phoneagent.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lewis.phoneagent.core.AppContainer
import com.lewis.phoneagent.core.Dispatcher
import com.lewis.phoneagent.core.DispatcherResponse
import com.lewis.phoneagent.core.LlmStreamEvent
import com.lewis.phoneagent.core.MemoryRecorder
import com.lewis.phoneagent.core.SessionStore
import com.lewis.phoneagent.data.ImageMemory
import com.lewis.phoneagent.data.OcrEngine
import com.lewis.phoneagent.data.PermissionChecker
import com.lewis.phoneagent.data.db.HistoryDao
import com.lewis.phoneagent.model.ChatMessage
import com.lewis.phoneagent.model.ConfirmPayload
import com.lewis.phoneagent.model.ContentType
import com.lewis.phoneagent.model.Role
import com.lewis.phoneagent.tools.ToolResult
import com.lewis.phoneagent.tools.ToolResultDto
import com.lewis.phoneagent.tools.toDto
import com.lewis.phoneagent.util.AppJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 聊天页 ViewModel：UI 只跟它说话，它只跟 Dispatcher 说话。
 * 消息持久化到 Room，重启 App 后恢复上一次会话。
 */
class ChatViewModel(
    private val dispatcher: Dispatcher,
    private val historyDao: HistoryDao,
    private val permissionChecker: PermissionChecker,
    private val sessionStore: SessionStore,
    private val memoryRecorder: MemoryRecorder,
    private val ocrEngine: OcrEngine,
    private val imageMemory: ImageMemory,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    /** 当前会话 id（新开会话/历史切换都改它） */
    private val _currentSession = MutableStateFlow(sessionStore.current())
    val currentSession: StateFlow<Long> = _currentSession.asStateFlow()

    /** 动态快捷指令：全局记忆里的高频应用（如「打开微信」），随使用习惯变化 */
    private val _quickCommands = MutableStateFlow<List<String>>(emptyList())
    val quickCommands: StateFlow<List<String>> = _quickCommands.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /** 因权限未授权而暂停的指令：用户授权回来后自动重试 */
    private var pendingText: String? = null

    /** Agent 循环里正在等待用户确认的操作（同一时刻最多一个） */
    private var pendingApproval: PendingApproval? = null

    private data class PendingApproval(
        val actionId: String,
        val messageId: Long,
        val deferred: CompletableDeferred<Boolean>,
    )

    init {
        viewModelScope.launch {
            _messages.value = historyDao.recent(_currentSession.value, 200).asReversed()
            refreshQuickCommands()
        }
    }

    /** 新开会话：旧会话保留在历史里，可随时切回 */
    fun newSession() {
        _currentSession.value = sessionStore.newSession()
        _messages.value = emptyList()
    }

    /** 从历史记录页切到指定会话 */
    fun switchSession(sessionId: Long) {
        if (sessionId == _currentSession.value) return
        sessionStore.switchTo(sessionId)
        _currentSession.value = sessionId
        viewModelScope.launch {
            _messages.value = historyDao.recent(sessionId, 200).asReversed()
        }
    }

    /** 刷新动态快捷指令（每次工具执行后调用，习惯变了指令跟着变） */
    fun refreshQuickCommands() {
        viewModelScope.launch {
            _quickCommands.value = memoryRecorder.topFrequentApps(2).map { "打开${it.key}" }
        }
    }

    /**
     * 拍照完成：图片消息入聊天 → 自动本地 OCR 识字 → 识别成功自动存入错题本。
     * OCR 失败（如首次模型未下载）时明确提示，不静默失败。
     */
    fun onImageCaptured(imagePath: String) {
        imageMemory.set(imagePath) // 记录最近图片，供「看图」工具使用
        append(Role.USER, ContentType.IMAGE, imagePath)
        viewModelScope.launch {
            val placeholder = ChatMessage(
                sessionId = _currentSession.value,
                role = Role.ASSISTANT, contentType = ContentType.TEXT, content = "Recognizing text…",
            )
            val placeholderId = historyDao.insert(placeholder)
            _messages.update { it + placeholder.copy(id = placeholderId) }
            val text = try {
                ocrEngine.recognize(imagePath)
            } catch (e: Exception) {
                "OCR failed: ${e.message ?: e.javaClass.simpleName}. " +
                    "First recognition downloads the offline model (~20MB); check the network and retry."
            }
            rewrite(placeholderId, ContentType.TEXT, text)

            // 识别成功 → 自动走 Agent 循环存入错题本（识别失败/空白只显示文字，不误存）
            val recognized = text.isNotBlank() &&
                !text.startsWith("OCR failed") &&
                text != "（没有识别到文字）"
            if (recognized) {
                send("把上面拍到的错题存进错题本：学科从内容判断，判断不出就标「未分类」")
            }
        }
    }

    fun onInputChange(text: String) {
        _inputText.value = text
    }

    /** App 回到前台：权限已就绪则自动重试之前因授权暂停的指令 */
    fun onAppResume() {
        val pending = pendingText ?: return
        pendingText = null
        if (permissionChecker.hasUsageAccess()) {
            send(pending)
        }
    }

    /** 发送一条消息：存库 → 调度 → 回复存库 */
    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _sending.value) return
        viewModelScope.launch {
            _inputText.value = ""
            append(Role.USER, ContentType.TEXT, trimmed)

            _sending.value = true
            val response = try {
                dispatcher.process(trimmed)
            } catch (e: Exception) {
                DispatcherResponse.Text(
                    "出错了：${e.message ?: e.javaClass.simpleName}",
                    DispatcherResponse.Source.LOCAL_RULE,
                )
            }
            _sending.value = false

            when (response) {
                is DispatcherResponse.ToolOutcome -> {
                    if (response.result is ToolResult.NeedPermission) {
                        pendingText = trimmed // 授权回来自动重试
                    }
                    append(
                        Role.ASSISTANT, ContentType.TOOL_RESULT,
                        AppJson.json.encodeToString(
                            ToolResultDto.serializer(), response.result.toDto(),
                        ),
                    )
                    refreshQuickCommands() // 执行完刷新高频指令
                }
                is DispatcherResponse.ConfirmRequired -> append(
                    Role.ASSISTANT, ContentType.CONFIRM,
                    AppJson.json.encodeToString(
                        ConfirmPayload.serializer(),
                        ConfirmPayload(response.actionId, response.title, response.description),
                    ),
                )
                is DispatcherResponse.Text -> append(Role.ASSISTANT, ContentType.TEXT, response.text)
                is DispatcherResponse.Placeholder -> startAgentTask(trimmed)
            }
        }
    }

    /**
     * Agent 循环：本地未命中时调用，LLM 主导直到任务完成。
     * 先插入一条空的助手消息占位，然后按事件流式更新它：
     * Delta 增量渲染（跨轮累积，含工具执行进度行）→ FinalText/Error 定稿；
     * 模型要执行危险操作时出确认卡片，等用户点确认/取消后循环继续。
     */
    private suspend fun startAgentTask(text: String) {
        val msg = ChatMessage(
            sessionId = _currentSession.value,
            role = Role.ASSISTANT, contentType = ContentType.TEXT, content = "",
        )
        val msgId = historyDao.insert(msg)
        _messages.update { it + msg.copy(id = msgId) } // 用 Room 返回的真实 id，保证 key 唯一
        var buffer = StringBuilder()

        dispatcher.processAgent(
            text = text,
            history = _messages.value.filter { it.id != msgId },
            onEvent = { event ->
                when (event) {
                    is LlmStreamEvent.Delta -> {
                        buffer.append(event.text)
                        updateContentInMemory(msgId, buffer.toString())
                    }
                    is LlmStreamEvent.FinalText -> {
                        rewrite(msgId, ContentType.TEXT, event.text.ifBlank { "（模型没有返回内容）" })
                    }
                    is LlmStreamEvent.Error -> {
                        val finalText = if (buffer.isEmpty()) event.message else buffer.toString()
                        rewrite(msgId, ContentType.TEXT, finalText)
                    }
                    // ToolCall 不走这里：确认交互在 approve 回调里处理
                    is LlmStreamEvent.ToolCall -> Unit
                }
            },
            approve = { toolCall ->
                // 危险操作：消息改写为确认卡片，循环挂起等用户点确认/取消
                val deferred = CompletableDeferred<Boolean>()
                pendingApproval = PendingApproval(toolCall.actionId, msgId, deferred)
                rewrite(
                    msgId, ContentType.CONFIRM,
                    AppJson.json.encodeToString(
                        ConfirmPayload.serializer(),
                        ConfirmPayload(toolCall.actionId, toolCall.title, toolCall.description),
                    ),
                )
                deferred.await()
            },
        )
    }

    /** 流式渲染中：只更新内存（不频繁写库），定稿时才写库。
     *  确认卡片被确认后循环继续输出，消息要从卡片形态变回文本形态 */
    private fun updateContentInMemory(id: Long, content: String) {
        _messages.update { list ->
            list.map {
                if (it.id == id) it.copy(contentType = ContentType.TEXT, content = content) else it
            }
        }
    }

    /** 确认卡片 → 确认执行。Agent 循环里的确认唤醒循环继续；本地快速通道走旧路径 */
    fun confirm(messageId: Long, actionId: String) {
        val approval = pendingApproval
        if (approval != null && approval.actionId == actionId) {
            pendingApproval = null
            approval.deferred.complete(true)
            return
        }
        viewModelScope.launch {
            val response = dispatcher.confirm(actionId)
            when (response) {
                is DispatcherResponse.ToolOutcome -> {
                    // 确认卡片改写为结果
                    rewrite(messageId, ContentType.TOOL_RESULT,
                        AppJson.json.encodeToString(ToolResultDto.serializer(), response.result.toDto()))
                }
                else -> rewrite(
                    messageId, ContentType.TEXT,
                    (response as? DispatcherResponse.Text)?.text ?: "操作未完成",
                )
            }
        }
    }

    /** 确认卡片 → 取消。Agent 循环里的取消告知循环跳过该操作 */
    fun cancel(messageId: Long, actionId: String) {
        val approval = pendingApproval
        if (approval != null && approval.actionId == actionId) {
            pendingApproval = null
            approval.deferred.complete(false)
            return
        }
        dispatcher.cancel(actionId)
        viewModelScope.launch { rewrite(messageId, ContentType.TEXT, "❌ 已取消") }
    }

    /** 追加消息并入库。临时用负数 id 占位（保证 LazyColumn key 唯一），入库后回填真实 id */
    private fun append(role: Role, type: ContentType, content: String) {
        val tempId = -System.currentTimeMillis()
        val msg = ChatMessage(
            id = tempId, sessionId = _currentSession.value,
            role = role, contentType = type, content = content,
        )
        _messages.update { it + msg }
        viewModelScope.launch {
            val realId = historyDao.insert(msg)
            _messages.update { list ->
                list.map { if (it.id == tempId) it.copy(id = realId) else it }
            }
        }
    }

    /** 改写已有消息（内存 + Room） */
    private suspend fun rewrite(id: Long, type: ContentType, content: String) {
        _messages.update { list ->
            list.map { if (it.id == id) it.copy(contentType = type, content = content) else it }
        }
        historyDao.updateContent(id, content, type.name)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    container.dispatcher,
                    container.historyDao,
                    container.permissionChecker,
                    container.sessionStore,
                    container.memoryRecorder,
                    container.ocrEngine,
                    container.imageMemory,
                )
            }
        }
    }
}
