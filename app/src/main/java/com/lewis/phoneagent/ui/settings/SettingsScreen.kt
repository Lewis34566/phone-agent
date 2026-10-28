package com.lewis.phoneagent.ui.settings

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lewis.phoneagent.PhoneAgentApp
import com.lewis.phoneagent.core.llm.LlmConfig
import com.lewis.phoneagent.core.llm.LlmConfigStore
import com.lewis.phoneagent.data.db.MemoryEntity
import kotlinx.coroutines.launch

/**
 * 设置页：
 *  - 权限状态（阶段②）
 *  - 云端模型配置：baseUrl/apiKey/model，apiKey 本地加密存储（阶段④）
 *  - 自定义规则管理 → 阶段⑤
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as PhoneAgentApp).container

    var usageGranted by remember { mutableStateOf(container.permissionChecker.hasUsageAccess()) }

    // LLM 配置表单状态（进入时加载已存配置；apiKey 不回显明文）
    val store = container.llmConfigStore
    val saved = remember { store.load() }
    var baseUrl by remember { mutableStateOf(saved.baseUrl.ifBlank { LlmConfigStore.DEFAULT_BASE_URL }) }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(saved.model.ifBlank { LlmConfigStore.DEFAULT_MODEL }) }
    val hasSavedKey = remember { store.hasApiKey() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- 权限状态 ----
            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Usage access", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (usageGranted) "✅ Granted" else "⚠️ Not granted (needed for: unused apps, full storage size, background ranking)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (!usageGranted) {
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }) { Text("Grant") }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Usage data is computed locally and never uploaded anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // ---- 云端模型（阶段④） ----
            Text("Cloud LLM (optional — local recognition doesn't need it)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Only called when local matching fails. DeepSeek is prefilled; any OpenAI-compatible service works.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("baseUrl") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(if (hasSavedKey) "apiKey (saved — leave blank to keep)" else "apiKey") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    val keyToSave = if (apiKey.isBlank() && hasSavedKey) saved.apiKey else apiKey
                    store.save(LlmConfig(baseUrl = baseUrl, apiKey = keyToSave, model = model))
                    Toast.makeText(context, "Saved (apiKey is encrypted locally)", Toast.LENGTH_SHORT).show()
                }) { Text("Save") }

                // 一键切到离线 ollama（手机 Termux 本机服务）
                OutlinedButton(onClick = {
                    baseUrl = "http://127.0.0.1:11434/v1"
                    apiKey = "ollama"
                    model = "qwen2.5:7b"
                    store.save(LlmConfig(baseUrl = baseUrl, apiKey = apiKey, model = model))
                    Toast.makeText(context, "Switched to on-device ollama (offline)", Toast.LENGTH_SHORT).show()
                }) { Text("Offline ollama") }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Offline mode: run ollama serve in Termux with a model pulled (e.g. qwen2.5:7b), tap the button to point at 127.0.0.1:11434 — cloud fallback works fully offline. For a desktop ollama use http://192.168.126.1:11434/v1 on the same Wi-Fi. apiKey is ignored by ollama. Image generation has no offline mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // ---- 生图服务（可选）：说「画一张XX」时调用 ----
            Text("Image generation (optional)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Cloud image API called when you say 'draw a …' (OpenAI-compatible). Video generation is not provided.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(10.dp))
            val genStore = container.imageGenConfigStore
            val genSaved = remember { genStore.load() }
            var genBaseUrl by remember { mutableStateOf(genSaved.baseUrl) }
            var genApiKey by remember { mutableStateOf("") }
            var genModel by remember { mutableStateOf(genSaved.model) }
            val genHasKey = remember { genStore.hasApiKey() }
            OutlinedTextField(
                value = genBaseUrl,
                onValueChange = { genBaseUrl = it },
                label = { Text("baseUrl（如 https://api.siliconflow.cn）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = genApiKey,
                onValueChange = { genApiKey = it },
                label = { Text(if (genHasKey) "apiKey (saved — leave blank to keep)" else "apiKey") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = genModel,
                onValueChange = { genModel = it },
                label = { Text("model（如 Kwai-Kolors/Kolors）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = {
                val keyToSave = if (genApiKey.isBlank() && genHasKey) genSaved.apiKey else genApiKey
                genStore.save(
                    com.lewis.phoneagent.core.llm.ImageGenConfigStore.Config(
                        baseUrl = genBaseUrl, apiKey = keyToSave, model = genModel,
                    ),
                )
                Toast.makeText(context, "Image config saved (apiKey encrypted locally)", Toast.LENGTH_SHORT).show()
            }) { Text("Save image config") }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // ---- 看图服务（可选）：拍照后说「这张图是什么」时调用 ----
            Text("Vision (optional)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Multimodal model for asking about photos (DeepSeek has no vision; use qwen-vl-plus etc.).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(10.dp))
            val visionStore = container.visionConfigStore
            val visionSaved = remember { visionStore.load() }
            var visionBaseUrl by remember { mutableStateOf(visionSaved.baseUrl) }
            var visionApiKey by remember { mutableStateOf("") }
            var visionModel by remember { mutableStateOf(visionSaved.model) }
            val visionHasKey = remember { visionStore.hasApiKey() }
            OutlinedTextField(
                value = visionBaseUrl,
                onValueChange = { visionBaseUrl = it },
                label = { Text("baseUrl（如 https://dashscope.aliyuncs.com/compatible-mode/v1）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = visionApiKey,
                onValueChange = { visionApiKey = it },
                label = { Text(if (visionHasKey) "apiKey (saved — leave blank to keep)" else "apiKey") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = visionModel,
                onValueChange = { visionModel = it },
                label = { Text("model（如 qwen-vl-plus）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = {
                val keyToSave = if (visionApiKey.isBlank() && visionHasKey) visionSaved.apiKey else visionApiKey
                visionStore.save(
                    com.lewis.phoneagent.core.llm.VisionConfigStore.Config(
                        baseUrl = visionBaseUrl, apiKey = keyToSave, model = visionModel,
                    ),
                )
                Toast.makeText(context, "Vision config saved (apiKey encrypted locally)", Toast.LENGTH_SHORT).show()
            }) { Text("Save vision config") }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // ---- 我的记忆（Agent 的全局 memory，只在本机） ----
            Text("My memory", style = MaterialTheme.typography.titleMedium)
            Text(
                "What the Agent remembers about your habits (local only). Frequent items surface as quick commands. Tap 🗑 to delete.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(8.dp))
            var memories by remember { mutableStateOf<List<MemoryEntity>>(emptyList()) }
            val scope = rememberCoroutineScope()
            fun reloadMemories() {
                scope.launch { memories = container.memoryDao.all() }
            }
            LaunchedEffect(Unit) { reloadMemories() }
            if (memories.isEmpty()) {
                Text(
                    "Nothing remembered yet. Use the app a few times and habits will show up here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                memories.take(20).forEach { m ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (m.type) {
                                    "app_usage" -> "Frequent app: ${m.key} (${m.count}×)"
                                    "tool" -> "Frequent feature: ${m.key} (${m.count}×)"
                                    else -> "${m.key}（${m.count} 次）"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                container.memoryDao.delete(m.id)
                                reloadMemories()
                            }
                        }) { Text("🗑") }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // ---- 自定义规则（表已建好，UI 后续版本做） ----
            Text("Custom rules", style = MaterialTheme.typography.titleMedium)
            Text(
                "Personal 'say X → do Y' rules arrive in a future release (schema ready).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
