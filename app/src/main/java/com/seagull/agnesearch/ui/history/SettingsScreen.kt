package com.seagull.agnesearch.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seagull.agnesearch.core.AppSettings
import com.seagull.agnesearch.core.Store
import com.seagull.agnesearch.net.AgnesClient
import com.seagull.agnesearch.net.Http
import com.seagull.agnesearch.ui.components.AppTopBar
import com.seagull.agnesearch.ui.components.FieldLabel
import com.seagull.agnesearch.ui.components.GlassCard
import com.seagull.agnesearch.ui.components.Pill
import com.seagull.agnesearch.ui.components.SectionTitle
import com.seagull.agnesearch.ui.components.SliderRow
import com.seagull.agnesearch.ui.components.SwitchRow
import com.seagull.agnesearch.ui.components.TextInput
import com.seagull.agnesearch.ui.theme.brand
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val br = brand
    val cfg by Store.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var testResult by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var customModel by remember { mutableStateOf(if (AppSettings.MODELS.contains(cfg.model)) "" else cfg.model) }

    fun save(block: (AppSettings) -> AppSettings) {
        Store.updateSettings(block)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "接口设置",
            onBack = onBack,
            actions = {
                androidx.compose.material3.IconButton(onClick = {
                    save { AppSettings() }
                    Http.applyProxy("")
                    testResult = "已恢复默认配置"
                }) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "恢复默认",
                        tint = br.textSecondary,
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)),
        ) {

            // ── 接口 ────────────────────────────────────────────────
            SectionTitle("接口", "OpenAI 兼容")
            GlassCard(corner = 20, padding = 14) {
                Column {
                    FieldLabel("Base URL", "Agnes 默认 https://apihub.agnes-ai.com/v1")
                    TextInput(
                        value = cfg.baseUrl,
                        onValueChange = { v -> save { it.copy(baseUrl = v) } },
                        placeholder = AppSettings.DEFAULT_BASE_URL,
                    )

                    FieldLabel("API Key", "在 Agnes 控制台创建，只存在本机")
                    TextInput(
                        value = cfg.apiKey,
                        onValueChange = { v -> save { it.copy(apiKey = v) } },
                        placeholder = "sk-...",
                        password = true,
                    )

                    FieldLabel("模型", "点选或自己填")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (m in AppSettings.MODELS.take(3)) {
                            Pill(
                                text = m.removePrefix("agnes-"),
                                selected = cfg.model == m,
                                onClick = {
                                    customModel = ""
                                    save { it.copy(model = m) }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(
                            text = "agnes-2.5-pro-beta",
                            selected = cfg.model == "agnes-2.5-pro-beta",
                            onClick = {
                                customModel = ""
                                save { it.copy(model = "agnes-2.5-pro-beta") }
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    TextInput(
                        value = customModel,
                        onValueChange = { v ->
                            customModel = v
                            if (v.isNotBlank()) save { it.copy(model = v.trim()) }
                        },
                        placeholder = "自定义模型名（留空用上面的）",
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(br.accent, RoundedCornerShape(12.dp))
                                .clickable(enabled = !testing) {
                                    testing = true
                                    testResult = "测试中…"
                                    val snapshot = cfg
                                    scope.launch {
                                        val r = AgnesClient(snapshot).testConnection()
                                        testResult = r
                                        testing = false
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                if (testing) "测试中…" else "测试连接",
                                color = br.onAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        if (testResult.isNotEmpty()) {
                            Text(
                                testResult,
                                color = if (testResult.startsWith("✅")) br.textPrimary else br.danger,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // ── 搜索 ────────────────────────────────────────────────
            SectionTitle("搜索源", "无需额外 Key")
            GlassCard(corner = 20, padding = 14) {
                Column {
                    Text(
                        "App 自己抓公开搜索页，不依赖第三方搜索 API。国内网络建议留「自动」。",
                        color = br.textSecondary,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("自动", cfg.searchProvider == "auto") { save { it.copy(searchProvider = "auto") } }
                        Pill("Bing", cfg.searchProvider == "bing") { save { it.copy(searchProvider = "bing") } }
                        Pill("DDG", cfg.searchProvider == "duckduckgo") { save { it.copy(searchProvider = "duckduckgo") } }
                        Pill("SearXNG", cfg.searchProvider == "searxng") { save { it.copy(searchProvider = "searxng") } }
                    }

                    FieldLabel("SearXNG 实例地址", "填了之后「自动」会优先用它，需实例开启 format=json")
                    TextInput(
                        value = cfg.searxUrl,
                        onValueChange = { v -> save { it.copy(searxUrl = v) } },
                        placeholder = "https://searx.example.com",
                    )

                    SliderRow(
                        label = "每次结果数",
                        value = cfg.maxResults.toFloat(),
                        range = 3f..20f,
                        steps = 16,
                        display = cfg.maxResults.toString(),
                        onChange = { v -> save { it.copy(maxResults = v.toInt()) } },
                    )
                    SliderRow(
                        label = "自动阅读网页数",
                        value = cfg.fetchPages.toFloat(),
                        range = 0f..6f,
                        steps = 5,
                        display = if (cfg.fetchPages == 0) "关闭" else cfg.fetchPages.toString(),
                        onChange = { v -> save { it.copy(fetchPages = v.toInt()) } },
                    )
                }
            }

            // ── Agent ───────────────────────────────────────────────
            SectionTitle("Agent 行为")
            GlassCard(corner = 20, padding = 14) {
                Column {
                    SliderRow(
                        label = "最多检索轮次",
                        value = cfg.maxRounds.toFloat(),
                        range = 1f..6f,
                        steps = 4,
                        display = cfg.maxRounds.toString(),
                        onChange = { v -> save { it.copy(maxRounds = v.toInt()) } },
                    )
                    SliderRow(
                        label = "温度",
                        value = cfg.temperature.toFloat(),
                        range = 0f..1f,
                        steps = 19,
                        display = String.format("%.2f", cfg.temperature),
                        onChange = { v -> save { it.copy(temperature = (v * 100).toInt() / 100.0) } },
                    )
                    SliderRow(
                        label = "最大输出",
                        value = cfg.maxTokens.toFloat(),
                        range = 1024f..16384f,
                        steps = 14,
                        display = cfg.maxTokens.toString(),
                        onChange = { v -> save { it.copy(maxTokens = (v / 1024).toInt() * 1024) } },
                    )
                    Spacer(Modifier.height(6.dp))
                    SwitchRow(
                        label = "Thinking 模式",
                        desc = "编码 / 推理类问题更稳，速度略慢",
                        checked = cfg.thinking,
                        onChange = { v -> save { it.copy(thinking = v) } },
                    )
                    FieldLabel("自定义系统提示", "留空用内置的搜索 Agent 提示词")
                    TextInput(
                        value = cfg.systemPrompt,
                        onValueChange = { v -> save { it.copy(systemPrompt = v) } },
                        placeholder = "留空即可",
                        singleLine = false,
                        maxLines = 5,
                    )
                }
            }

            // ── 网络 ────────────────────────────────────────────────
            SectionTitle("网络")
            GlassCard(corner = 20, padding = 14) {
                Column {
                    FieldLabel("代理地址", "例如 http://127.0.0.1:7890，留空直连")
                    TextInput(
                        value = cfg.proxyUrl,
                        onValueChange = { v ->
                            save { it.copy(proxyUrl = v) }
                            Http.applyProxy(v)
                        },
                        placeholder = "http://127.0.0.1:7890",
                    )
                    Text(
                        "请求模型接口和抓取搜索结果都会走这个代理。",
                        color = br.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            // ── 外观 ────────────────────────────────────────────────
            SectionTitle("外观")
            GlassCard(corner = 20, padding = 14) {
                Column {
                    FieldLabel("主题")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("跟随系统", cfg.themeMode == "system") { save { it.copy(themeMode = "system") } }
                        Pill("浅色", cfg.themeMode == "light") { save { it.copy(themeMode = "light") } }
                        Pill("深色", cfg.themeMode == "dark") { save { it.copy(themeMode = "dark") } }
                    }
                    Spacer(Modifier.height(10.dp))
                    FieldLabel("回答语言")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("中文", cfg.language == "zh") { save { it.copy(language = "zh") } }
                        Pill("English", cfg.language == "en") { save { it.copy(language = "en") } }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "改完即生效，不用重启。Key 与配置都只保存在本机 SharedPreferences。",
                color = br.textSecondary,
                fontSize = 11.sp,
            )
        }
    }
}
