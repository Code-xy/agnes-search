package com.seagull.agnesearch.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seagull.agnesearch.core.AppSettings
import com.seagull.agnesearch.core.Chat
import com.seagull.agnesearch.core.ChatMessage
import com.seagull.agnesearch.core.Store
import com.seagull.agnesearch.net.AgnesClient
import com.seagull.agnesearch.net.StreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ChatViewModel : ViewModel() {

    var currentId by mutableStateOf<String?>(null)
        private set

    var draft by mutableStateOf("")

    var pendingImages by mutableStateOf<List<String>>(emptyList())
        private set

    var streaming by mutableStateOf(false)
        private set

    var streamText by mutableStateOf("")
        private set

    var error by mutableStateOf("")
        private set

    private var job: Job? = null

    fun current(): Chat? = Store.chats.value.firstOrNull { it.id == currentId }

    fun newChat() {
        job?.cancel()
        currentId = null
        draft = ""
        pendingImages = emptyList()
        streamText = ""
        streaming = false
        error = ""
    }

    fun open(id: String) {
        job?.cancel()
        currentId = id
        streamText = ""
        streaming = false
        error = ""
    }

    fun addImage(dataUri: String) {
        if (dataUri.isBlank()) return
        pendingImages = (pendingImages + dataUri).take(4)
    }

    fun removeImage(index: Int) {
        pendingImages = pendingImages.filterIndexed { i, _ -> i != index }
    }

    fun send() {
        val text = draft.trim()
        if (streaming) return
        if (text.isEmpty() && pendingImages.isEmpty()) return

        val cfg = Store.settings.value
        if (cfg.apiKey.isBlank()) {
            error = "还没填 API Key。点右上角进「接口设置」，填上 Agnes 的 Key 再聊。"
            return
        }

        var chat = current()
        if (chat == null) {
            chat = Chat(
                id = Store.newId(),
                title = titleOf(text),
                model = cfg.model,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            Store.addChat(chat)
            currentId = chat.id
        }

        val userMsg = ChatMessage(
            id = Store.newId(),
            role = "user",
            content = text,
            images = pendingImages,
            createdAt = System.currentTimeMillis(),
        )
        val history = chat.messages + userMsg
        val chatId = chat.id
        Store.updateChat(chatId) { c ->
            c.copy(
                messages = history,
                model = cfg.model,
                title = if (c.messages.isEmpty()) titleOf(text) else c.title,
            )
        }
        draft = ""
        pendingImages = emptyList()
        error = ""
        runCompletion(chatId, history)
    }

    /** 重发最后一条用户消息。 */
    fun regenerate() {
        val chat = current() ?: return
        if (streaming) return
        val msgs = chat.messages
        val lastUserIdx = msgs.indexOfLast { it.role == "user" }
        if (lastUserIdx < 0) return
        val trimmed = msgs.subList(0, lastUserIdx + 1).toList()
        Store.updateChat(chat.id) { it.copy(messages = trimmed) }
        error = ""
        runCompletion(chat.id, trimmed)
    }

    fun stop() {
        val chatId = currentId
        job?.cancel()
        job = null
        val partial = streamText
        if (chatId != null && partial.isNotBlank()) {
            Store.updateChat(chatId) { c ->
                c.copy(
                    messages = c.messages + ChatMessage(
                        id = Store.newId(),
                        role = "assistant",
                        content = partial,
                        model = Store.settings.value.model,
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
        }
        streamText = ""
        streaming = false
    }

    fun deleteChat(id: String) {
        if (currentId == id) newChat()
        Store.deleteChat(id)
    }

    private fun runCompletion(chatId: String, history: List<ChatMessage>) {
        val cfg = Store.settings.value
        streaming = true
        streamText = ""
        val started = System.currentTimeMillis()

        job = viewModelScope.launch {
            val client = AgnesClient(cfg)
            val sb = StringBuilder()
            var pt = 0
            var ct = 0
            var failure: String? = null

            try {
                client.stream(buildMessages(cfg, history)).collect { ev ->
                    when (ev) {
                        is StreamEvent.Text -> {
                            sb.append(ev.delta)
                            streamText = sb.toString()
                        }

                        is StreamEvent.Completed -> {
                            pt = ev.promptTokens
                            ct = ev.completionTokens
                        }

                        is StreamEvent.Failure -> failure = ev.message

                        else -> {}
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e.message ?: e.javaClass.simpleName
            }

            val text = sb.toString()
            val failMsg = failure
            val content = when {
                text.isNotBlank() -> text
                failMsg != null -> "请求失败：" + failMsg
                else -> "（模型没有返回内容，检查一下模型名和接口设置）"
            }
            Store.updateChat(chatId) { c ->
                c.copy(
                    messages = c.messages + ChatMessage(
                        id = Store.newId(),
                        role = "assistant",
                        content = content,
                        model = cfg.model,
                        promptTokens = pt,
                        completionTokens = ct,
                        elapsedMs = System.currentTimeMillis() - started,
                        error = text.isBlank(),
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
            if (pt > 0 || ct > 0) Store.addUsage(pt, ct, 1)
            streamText = ""
            streaming = false
        }
    }

    // ── 请求体构造 ──────────────────────────────────────────────────────

    private fun buildMessages(cfg: AppSettings, history: List<ChatMessage>): JsonArray {
        val out = ArrayList<JsonObject>()
        val sys = systemPrompt(cfg)
        if (sys.isNotBlank()) {
            out.add(buildJsonObject {
                put("role", "system")
                put("content", sys)
            })
        }
        for (m in history.takeLast(cfg.contextLimit.coerceIn(2, 100))) {
            if (m.content.isBlank() && m.images.isEmpty()) continue
            if (m.error) continue
            if (m.images.isEmpty()) {
                out.add(buildJsonObject {
                    put("role", m.role)
                    put("content", m.content)
                })
            } else {
                out.add(buildJsonObject {
                    put("role", m.role)
                    put("content", buildJsonArray {
                        if (m.content.isNotBlank()) {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", m.content)
                                } as JsonElement
                            )
                        }
                        for (img in m.images) {
                            add(
                                buildJsonObject {
                                    put("type", "image_url")
                                    put("image_url", buildJsonObject { put("url", img) })
                                } as JsonElement
                            )
                        }
                    })
                })
            }
        }
        return JsonArray(out)
    }

    private fun systemPrompt(cfg: AppSettings): String {
        if (cfg.systemPrompt.isNotBlank()) return cfg.systemPrompt
        return if (cfg.language == "en") {
            "You are a helpful, precise assistant. Answer in Markdown when it helps. Be concise."
        } else {
            "你是一个专业、直接的助手。回答准确、不啰嗦，需要结构化时用 Markdown（分点、表格、代码块）。不确定的地方要说明。"
        }
    }

    private fun titleOf(text: String): String {
        val t = text.replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return "图片对话"
        return if (t.length <= 18) t else t.substring(0, 18) + "…"
    }

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
    }
}
