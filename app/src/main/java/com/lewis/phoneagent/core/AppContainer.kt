package com.lewis.phoneagent.core

import android.content.Context
import com.lewis.phoneagent.core.intent.AppNameResolver
import com.lewis.phoneagent.core.llm.ImageGenConfigStore
import com.lewis.phoneagent.core.llm.LlmConfigStore
import com.lewis.phoneagent.core.llm.OpenAiCompatImageProvider
import com.lewis.phoneagent.core.llm.OpenAiCompatProvider
import com.lewis.phoneagent.core.llm.OpenAiCompatVisionProvider
import com.lewis.phoneagent.core.llm.VisionConfigStore
import com.lewis.phoneagent.data.AppRepository
import com.lewis.phoneagent.data.ImageMemory
import com.lewis.phoneagent.data.MistakeRepository
import com.lewis.phoneagent.data.OcrEngine
import com.lewis.phoneagent.data.PermissionChecker
import com.lewis.phoneagent.data.UsageRepository
import com.lewis.phoneagent.data.db.AppDatabase
import com.lewis.phoneagent.data.db.HistoryDao
import com.lewis.phoneagent.data.db.MemoryDao
import com.lewis.phoneagent.data.db.MistakeNoteDao
import com.lewis.phoneagent.data.db.NoteDao
import com.lewis.phoneagent.data.db.RuleDao
import com.lewis.phoneagent.tools.ToolRegistry
import com.lewis.phoneagent.tools.action.AppSettingsTool
import com.lewis.phoneagent.tools.action.GameTool
import com.lewis.phoneagent.tools.action.ImageGenTool
import com.lewis.phoneagent.tools.action.OpenAppTool
import com.lewis.phoneagent.tools.action.UninstallAppTool
import com.lewis.phoneagent.tools.notebook.AddMistakeTool
import com.lewis.phoneagent.tools.notebook.MarkMistakeTool
import com.lewis.phoneagent.tools.notebook.ReviewMistakesTool
import com.lewis.phoneagent.tools.notebook.SearchMistakesTool
import com.lewis.phoneagent.tools.query.AppDetailTool
import com.lewis.phoneagent.tools.query.AppPermissionsTool
import com.lewis.phoneagent.tools.query.BackgroundRankTool
import com.lewis.phoneagent.tools.query.ListAppsTool
import com.lewis.phoneagent.tools.query.NoteTool
import com.lewis.phoneagent.tools.query.SearchTool
import com.lewis.phoneagent.tools.query.StorageTool
import com.lewis.phoneagent.tools.query.UnusedAppsTool
import com.lewis.phoneagent.tools.query.VisionTool

/**
 * 手动依赖注入容器：所有单例在这里创建并互相接线。
 *
 * 不引入 Hilt 的原因：MVP 规模下手动注入足够，且少一层 KSP 处理，构建更快、排错更直白。
 * 每个依赖都是 lazy，保证冷启动时 Application.onCreate 零开销。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    // ---- 数据层 ----
    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val historyDao: HistoryDao by lazy { database.historyDao() }
    val ruleDao: RuleDao by lazy { database.ruleDao() }
    val memoryDao: MemoryDao by lazy { database.memoryDao() }
    val noteDao: NoteDao by lazy { database.noteDao() }
    val mistakeNoteDao: MistakeNoteDao by lazy { database.mistakeNoteDao() }
    val mistakeRepository: MistakeRepository by lazy { MistakeRepository(mistakeNoteDao) }

    val sessionStore: SessionStore by lazy { SessionStore(appContext) }
    val memoryRecorder: MemoryRecorder by lazy { MemoryRecorder(memoryDao) }

    val appRepository: AppRepository by lazy { AppRepository(appContext) }
    val usageRepository: UsageRepository by lazy { UsageRepository(appContext) }
    val permissionChecker: PermissionChecker by lazy { PermissionChecker(appContext) }
    val appNameResolver: AppNameResolver by lazy { AppNameResolver(appRepository) }
    val ocrEngine: OcrEngine by lazy { OcrEngine(appContext) }
    val imageMemory: ImageMemory by lazy { ImageMemory() }

    // ---- 工具层 ----
    // 新增工具 = 写一个类实现 Tool 接口 + 在这里注册一行，调度层零改动。
    val toolRegistry: ToolRegistry by lazy {
        ToolRegistry(
            listOf(
                // 查询类（阶段②）
                ListAppsTool(appRepository),
                UnusedAppsTool(appRepository, usageRepository, permissionChecker),
                AppPermissionsTool(appContext, appNameResolver),
                BackgroundRankTool(appRepository, usageRepository, permissionChecker),
                // 操作类（阶段③）
                OpenAppTool(appContext, appNameResolver),
                UninstallAppTool(appContext, appNameResolver),
                AppSettingsTool(appContext, appNameResolver),
                // 存储空间管家
                StorageTool(appRepository),
                // 便签（办公能力，纯本机离线可用）
                NoteTool(noteDao),
                // 错题本（v1 核心场景：拍照→OCR→遗忘曲线→提醒复习）
                AddMistakeTool(mistakeRepository),
                ReviewMistakesTool(mistakeRepository),
                MarkMistakeTool(mistakeRepository),
                SearchMistakesTool(mistakeRepository),
                // 联网搜索（Phone-Vibe 能力，零配置）
                SearchTool(),
                // 应用详情（软件全管理核心查询）
                AppDetailTool(appContext, appNameResolver, appRepository, usageRepository, permissionChecker),
                // 看图理解（多模态模型，需配置）
                VisionTool(imageMemory, visionProvider),
                // 生图（云端服务，用户配置后可用）
                ImageGenTool(appContext, imageGenProvider),
                // 小游戏（本地离线可玩）
                GameTool(),
            ),
        )
    }

    // ---- LLM 层（可替换模块：换模型只需换 provider 实现，调度层无感知） ----
    val llmConfigStore: LlmConfigStore by lazy { LlmConfigStore(appContext) }
    val llmProvider: OpenAiCompatProvider by lazy { OpenAiCompatProvider(llmConfigStore) }

    // ---- 生图（独立模块：与对话模型分开配置，服务商可换） ----
    val imageGenConfigStore: ImageGenConfigStore by lazy { ImageGenConfigStore(appContext) }
    val imageGenProvider: OpenAiCompatImageProvider by lazy { OpenAiCompatImageProvider(imageGenConfigStore) }

    // ---- 看图理解（多模态模型，独立配置） ----
    val visionConfigStore: VisionConfigStore by lazy { VisionConfigStore(appContext) }
    val visionProvider: OpenAiCompatVisionProvider by lazy { OpenAiCompatVisionProvider(visionConfigStore) }

    // ---- 调度层 ----
    val dispatcher: Dispatcher by lazy {
        Dispatcher(toolRegistry, llmProvider = llmProvider, memoryRecorder = memoryRecorder)
    }
}
