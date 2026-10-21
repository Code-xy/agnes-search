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
    private const val K_SESSIONS = "sessions"
    private const val K_USAGE = "usage"
    private const val MAX_SESSIONS = 200

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private var sp: SharedPreferences? = null

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions

    private val _usage = MutableStateFlow(UsageStat())
    val usage: StateFlow<UsageStat> = _usage

    fun init(context: Context) {
        if (sp != null) return
        val p = context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        sp = p
        _settings.value = readJson(p.getString(K_SETTINGS, null), AppSettings.serializer()) ?: AppSettings()
        _sessions.value = readJson(
            p.getString(K_SESSIONS, null),
            ListSerializer(Session.serializer())
        ) ?: emptyList()
        _usage.value = readJson(p.getString(K_USAGE, null), UsageStat.serializer()) ?: UsageStat()
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

    fun addSession(session: Session): Session {
        val list = (_sessions.value.filterNot { it.id == session.id } + session)
            .sortedByDescending { it.createdAt }
            .take(MAX_SESSIONS)
        _sessions.value = list
        write(K_SESSIONS, list, ListSerializer(Session.serializer()))
        return session
    }

    fun toggleStar(id: String) {
        val list = _sessions.value.map { if (it.id == id) it.copy(starred = !it.starred) else it }
        _sessions.value = list
        write(K_SESSIONS, list, ListSerializer(Session.serializer()))
    }

    fun deleteSession(id: String) {
        val list = _sessions.value.filterNot { it.id == id }
        _sessions.value = list
        write(K_SESSIONS, list, ListSerializer(Session.serializer()))
    }

    fun clearSessions() {
        _sessions.value = emptyList()
        write(K_SESSIONS, emptyList<Session>(), ListSerializer(Session.serializer()))
    }

    // ── 用量 ────────────────────────────────────────────────────────────

    fun addUsage(promptTokens: Int, completionTokens: Int, searches: Int = 0) {
        val cur = _usage.value
        val next = cur.copy(
            calls = cur.calls + 1,
            promptTokens = cur.promptTokens + promptTokens,
            completionTokens = cur.completionTokens + completionTokens,
            searches = cur.searches + searches,
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

    companion object {
        fun newId(): String = "s" + System.currentTimeMillis().toString(36) +
            Integer.toHexString((Math.random() * 0xFFFF).toInt())
    }
}
