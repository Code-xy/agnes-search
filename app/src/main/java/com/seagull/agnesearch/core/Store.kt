package com.seagull.agnesearch.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 极简本地存储：SharedPreferences + kotlinx-serialization。
 * 所有状态都暴露成 StateFlow，Compose 直接 collectAsState 即可。
 */
object Store {

    private const val SP_NAME = "agnes_search"
    private const val K_SETTINGS = "settings"
    private const val K_CHATS = "chats"
    private const val K_USAGE = "usage"
    private const val K_CREATIONS = "creations"
    private const val MAX_CHATS = 200

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private var sp: SharedPreferences? = null

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats

    private val _usage = MutableStateFlow(UsageStat())
    val usage: StateFlow<UsageStat> = _usage

    private val _creations = MutableStateFlow<List<Creation>>(emptyList())
    val creations: StateFlow<List<Creation>> = _creations

    fun init(context: Context) {
        if (sp != null) return
        val p = context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        sp = p
        _settings.value = readJson(p.getString(K_SETTINGS, null), AppSettings.serializer()) ?: AppSettings()
        _chats.value = readJson(
            p.getString(K_CHATS, null),
            ListSerializer(Chat.serializer())
        ) ?: emptyList()
        _usage.value = readJson(p.getString(K_USAGE, null), UsageStat.serializer()) ?: UsageStat()
        _creations.value = readJson(
            p.getString(K_CREATIONS, null),
            ListSerializer(Creation.serializer())
        ) ?: emptyList()
    }

    private fun <T> readJson(text: String?, serializer: KSerializer<T>): T? {
        if (text.isNullOrBlank()) return null
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: Exception) {
            null
        }
    }

    private fun <T> write(key: String, value: T, serializer: KSerializer<T>) {
        try {
            sp?.edit()?.putString(key, json.encodeToString(serializer, value))?.apply()
        } catch (e: Exception) {
            // 存储失败不该影响主流程
        }
    }

    // ── 设置 ────────────────────────────────────────────────────────────

    fun updateSettings(block: (AppSettings) -> AppSettings) {
        val next = block(_settings.value)
        _settings.value = next
        write(K_SETTINGS, next, AppSettings.serializer())
    }

    fun setSettings(value: AppSettings) = updateSettings { value }

    fun resetSettings() = setSettings(AppSettings())

    // ── 会话 ────────────────────────────────────────────────────────────

    fun addChat(chat: Chat): Chat {
        val list = (_chats.value.filterNot { it.id == chat.id } + chat)
            .sortedByDescending { it.updatedAt }
            .take(MAX_CHATS)
        _chats.value = list
        write(K_CHATS, list, ListSerializer(Chat.serializer()))
        return chat
    }

    fun updateChat(id: String, block: (Chat) -> Chat) {
        val list = _chats.value.map {
            if (it.id == id) block(it).copy(updatedAt = System.currentTimeMillis()) else it
        }.sortedByDescending { it.updatedAt }
        _chats.value = list
        write(K_CHATS, list, ListSerializer(Chat.serializer()))
    }

    fun toggleChatStar(id: String) {
        updateChat(id) { it.copy(starred = !it.starred) }
    }

    fun deleteChat(id: String) {
        val list = _chats.value.filterNot { it.id == id }
        _chats.value = list
        write(K_CHATS, list, ListSerializer(Chat.serializer()))
    }

    fun clearChats() {
        _chats.value = emptyList()
        write(K_CHATS, emptyList<Chat>(), ListSerializer(Chat.serializer()))
    }

    // ── 生成记录（图像 / 视频）────────────────────────────────────────────

    fun addCreation(c: Creation) {
        val list = (_creations.value.filterNot { it.id == c.id } + c)
            .sortedByDescending { it.createdAt }
            .take(120)
        _creations.value = list
        write(K_CREATIONS, list, ListSerializer(Creation.serializer()))
    }

    fun updateCreation(id: String, block: (Creation) -> Creation) {
        val list = _creations.value.map { if (it.id == id) block(it) else it }
        _creations.value = list
        write(K_CREATIONS, list, ListSerializer(Creation.serializer()))
    }

    fun toggleCreationStar(id: String) {
        updateCreation(id) { it.copy(starred = !it.starred) }
    }

    fun deleteCreation(id: String) {
        val list = _creations.value.filterNot { it.id == id }
        _creations.value = list
        write(K_CREATIONS, list, ListSerializer(Creation.serializer()))
    }

    fun clearCreations() {
        _creations.value = emptyList()
        write(K_CREATIONS, emptyList<Creation>(), ListSerializer(Creation.serializer()))
    }

    // ── 用量 ────────────────────────────────────────────────────────────

    fun addUsage(promptTokens: Int, completionTokens: Int, messages: Int = 0) {
        val cur = _usage.value
        val next = cur.copy(
            calls = cur.calls + 1,
            messages = cur.messages + messages,
            promptTokens = cur.promptTokens + promptTokens,
            completionTokens = cur.completionTokens + completionTokens,
            firstUseAt = if (cur.firstUseAt == 0L) System.currentTimeMillis() else cur.firstUseAt,
        )
        _usage.value = next
        write(K_USAGE, next, UsageStat.serializer())
    }

    fun bumpImages() {
        val cur = _usage.value
        val next = cur.copy(
            images = cur.images + 1,
            firstUseAt = if (cur.firstUseAt == 0L) System.currentTimeMillis() else cur.firstUseAt,
        )
        _usage.value = next
        write(K_USAGE, next, UsageStat.serializer())
    }

    fun bumpVideos() {
        val cur = _usage.value
        val next = cur.copy(
            videos = cur.videos + 1,
            firstUseAt = if (cur.firstUseAt == 0L) System.currentTimeMillis() else cur.firstUseAt,
        )
        _usage.value = next
        write(K_USAGE, next, UsageStat.serializer())
    }

    fun resetUsage() {
        val next = UsageStat()
        _usage.value = next
        write(K_USAGE, next, UsageStat.serializer())
    }

    /** 生成会话 id。 */
    fun newId(): String = "s" + System.currentTimeMillis().toString(36) +
        Integer.toHexString((Math.random() * 0xFFFF).toInt())
}
