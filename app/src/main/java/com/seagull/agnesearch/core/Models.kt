package com.seagull.agnesearch.core

import kotlinx.serialization.Serializable

/** 全局配置，全部可从「我的 → 接口设置」里改。 */
@Serializable
data class AppSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    /** auto | bing | duckduckgo | searxng */
    val searchProvider: String = "auto",
    val searxUrl: String = "",
    /** Agent 最多几轮工具调用 */
    val maxRounds: Int = 3,
    /** 每次搜索取几条结果 */
    val maxResults: Int = 8,
    /** 抓取正文的页面数（0 = 不抓） */
    val fetchPages: Int = 3,
    val temperature: Double = 0.3,
    val maxTokens: Int = 4096,
    /** 是否开启 Thinking（chat_template_kwargs.enable_thinking） */
    val thinking: Boolean = false,
    val systemPrompt: String = "",
    /** system | light | dark */
    val themeMode: String = "system",
    /** 自定义代理，例如 http://127.0.0.1:7890，留空直连 */
    val proxyUrl: String = "",
    /** 回答语言 */
    val language: String = "zh",
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://apihub.agnes-ai.com/v1"
        const val DEFAULT_MODEL = "agnes-2.5-flash"

        val MODELS = listOf(
            "agnes-2.5-flash",
            "agnes-3.0-flash",
            "agnes-2.5-pro",
            "agnes-2.5-pro-beta",
        )
    }
}

/** 搜索命中的一条来源。 */
@Serializable
data class SourceItem(
    val title: String,
    val url: String,
    val snippet: String = "",
    val engine: String = "",
    /** 抓取到的正文（前若干字符） */
    val content: String = "",
)

/** 一次搜索会话（历史记录）。 */
@Serializable
data class Session(
    val id: String,
    val query: String,
    val answer: String = "",
    val sources: List<SourceItem> = emptyList(),
    val steps: List<String> = emptyList(),
    val model: String = "",
    val createdAt: Long = 0L,
    val elapsedMs: Long = 0L,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val starred: Boolean = false,
)

/** 本地累计用量。 */
@Serializable
data class UsageStat(
    val calls: Int = 0,
    val promptTokens: Long = 0L,
    val completionTokens: Long = 0L,
    val searches: Int = 0,
    val firstUseAt: Long = 0L,
)

/** 会话内的消息（发给模型的原始结构）。 */
@Serializable
data class ChatTurn(
    val role: String,
    val content: String = "",
    /** assistant 发起工具调用时保留原始 tool_calls JSON 文本 */
    val toolCallsJson: String = "",
    val toolCallId: String = "",
    val toolName: String = "",
)
