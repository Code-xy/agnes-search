package com.seagull.agnesearch.core

import kotlinx.serialization.Serializable

/** 全局配置，全部可从「我的 → 接口设置」里改。 */
@Serializable
data class AppSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,

    // ── 对话行为 ──
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
    /** 是否开启 Thinking（chat_template_kwargs.enable_thinking） */
    val thinking: Boolean = false,
    /** 每次请求带上多少条历史消息 */
    val contextLimit: Int = 20,
    val systemPrompt: String = "",
    /** 流式输出 */
    val stream: Boolean = true,

    val themeMode: String = "system",
    /** 自定义代理，例如 http://127.0.0.1:7890，留空直连 */
    val proxyUrl: String = "",
    /** zh | en，控制内置系统提示的语言 */
    val language: String = "zh",

    // ── 图像 ──
    val imageModel: String = "agnes-image-2.5-flash",
    /** 档位式尺寸：1K / 2K / 3K / 4K（2.0 模型走精确尺寸时自动换算） */
    val imageSize: String = "1K",
    val imageRatio: String = "1:1",
    val autoSaveImage: Boolean = true,

    // ── 视频 ──
    val videoModel: String = "agnes-video-2.5",
    val videoMode: String = "text",
    val videoSize: String = "720P",
    val videoAspect: String = "16:9",
    val videoSeconds: String = "5",
    val autoSaveVideo: Boolean = false,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://apihub.agnes-ai.com/v1"
        const val DEFAULT_MODEL = "agnes-2.5-flash"

        val MODELS = listOf(
            "agnes-3.0-flash",
            "agnes-2.5-flash",
            "agnes-2.5-pro",
            "agnes-2.5-pro-beta",
        )

        val IMAGE_MODELS = listOf(
            "agnes-image-2.5-flash",
            "agnes-image-2.1-flash",
            "agnes-image-2.0-flash",
        )

        val VIDEO_MODELS = listOf(
            "agnes-video-2.5",
            "agnes-video-2.5-flash",
        )

        val IMAGE_SIZES = listOf("1K", "2K", "3K", "4K")
        val IMAGE_RATIOS = listOf("1:1", "4:3", "3:4", "16:9", "9:16", "3:2", "2:3", "21:9")
        val VIDEO_SIZES = listOf("720P", "1080P", "1K", "2K")
        val VIDEO_ASPECTS = listOf("16:9", "9:16", "1:1", "4:3", "3:4", "21:9")
        val VIDEO_SECONDS = listOf("4", "5", "8", "10", "12")
    }
}

/** 消息里的附件。 */
@Serializable
data class Attachment(
    val id: String = "",
    val name: String,
    val size: Long = 0L,
    /** text（代码/纯文本，内容已读出来）| image（data URI）| other（接口不支持，只留元信息） */
    val kind: String = "text",
    val text: String = "",
    val dataUri: String = "",
    val mime: String = "",
    val note: String = "",
)

/** 聊天里的一条消息。 */
@Serializable
data class ChatMessage(
    val id: String = "",
    /** user | assistant */
    val role: String = "user",
    val content: String = "",
    /** data URI 或公网 URL，多模态图像输入 */
    val images: List<String> = emptyList(),
    val files: List<Attachment> = emptyList(),
    val model: String = "",
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val elapsedMs: Long = 0L,
    val error: Boolean = false,
    val createdAt: Long = 0L,
)

/** 一个会话。 */
@Serializable
data class Chat(
    val id: String,
    val title: String = "新对话",
    val messages: List<ChatMessage> = emptyList(),
    val model: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val starred: Boolean = false,
)

/** 一次图像 / 视频生成记录。 */
@Serializable
data class Creation(
    val id: String,
    /** image | video */
    val kind: String,
    val prompt: String,
    val model: String,
    /** queued | running | in_progress | done | failed */
    val status: String = "queued",
    val url: String = "",
    val localPath: String = "",
    val size: String = "",
    val ratio: String = "",
    val seconds: String = "",
    val mode: String = "",
    val progress: Int = 0,
    val error: String = "",
    val createdAt: Long = 0L,
    val elapsedMs: Long = 0L,
    val videoId: String = "",
    val starred: Boolean = false,
)

/** 本地累计用量。 */
@Serializable
data class UsageStat(
    val calls: Int = 0,
    val messages: Int = 0,
    val promptTokens: Long = 0L,
    val completionTokens: Long = 0L,
    val images: Int = 0,
    val videos: Int = 0,
    val firstUseAt: Long = 0L,
)
