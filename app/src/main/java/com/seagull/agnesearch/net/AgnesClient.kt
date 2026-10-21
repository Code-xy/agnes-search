package com.seagull.agnesearch.net

import com.seagull.agnesearch.core.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed class StreamEvent {
    /** 正文增量 */
    data class Text(val delta: String) : StreamEvent()
    /** 流式 tool_calls 分片 */
    data class ToolCallDelta(
        val index: Int,
        val id: String?,
        val name: String?,
        val args: String?,
    ) : StreamEvent()

    data class Completed(
        val promptTokens: Int,
        val completionTokens: Int,
        val finishReason: String,
    ) : StreamEvent()

    data class Failure(val message: String) : StreamEvent()
}

/**
 * Agnes / 任意 OpenAI 兼容端点的流式 Chat Completions 客户端。
 */
class AgnesClient(private val settings: AppSettings) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun endpoint(path: String): String {
        var base = settings.baseUrl.trim()
        if (base.isEmpty()) base = AppSettings.DEFAULT_BASE_URL
        base = base.trimEnd('/')
        // 用户可能填 https://host 或 https://host/v1，两种情况都能用
        val suffix = when {
            base.endsWith("/v1") -> "/"
            base.contains("/v1/") -> "/"
            else -> "/v1/"
        }
        return base + suffix + path.trimStart('/')
    }

    fun stream(
        messages: JsonArray,
        tools: JsonArray? = null,
    ): Flow<StreamEvent> = flow {
        if (settings.apiKey.isBlank()) {
            emit(StreamEvent.Failure("还没填 API Key，去「我的 → 接口设置」里填上"))
            return@flow
        }

        // 非流式：一次性拿完整回复
        if (!settings.stream) {
            val body = buildJsonObject {
                put("model", settings.model)
                put("messages", messages)
                put("temperature", settings.temperature)
                put("max_tokens", settings.maxTokens)
                if (tools != null && tools.isNotEmpty()) {
                    put("tools", tools)
                    put("tool_choice", "auto")
                }
                if (settings.thinking) {
                    put("chat_template_kwargs", buildJsonObject { put("enable_thinking", true) })
                }
            }
            val r = Request.Builder()
                .url(endpoint("chat/completions"))
                .header("Authorization", "Bearer " + settings.apiKey.trim())
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            try {
                Http.client().newCall(r).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        emit(StreamEvent.Failure(describeError(resp.code, text)))
                        return@flow
                    }
                    val o = json.parseToJsonElement(text).jsonObject
                    val ch = (o["choices"] as? JsonArray)?.firstOrNull()?.jsonObject
                    val msg = ch?.get("message") as? JsonObject
                    val content = msg?.get("content")?.jsonPrimitive?.contentOrNull
                        ?: msg?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
                        ?: ""
                    val u = o["usage"] as? JsonObject
                    val pt = u?.get("prompt_tokens")?.jsonPrimitive?.intOrNull
                        ?: u?.get("input_tokens")?.jsonPrimitive?.intOrNull ?: 0
                    val ct = u?.get("completion_tokens")?.jsonPrimitive?.intOrNull
                        ?: u?.get("output_tokens")?.jsonPrimitive?.intOrNull ?: 0
                    if (content.isNotEmpty()) emit(StreamEvent.Text(content))
                    emit(
                        StreamEvent.Completed(
                            pt,
                            ct,
                            ch?.get("finish_reason")?.jsonPrimitive?.contentOrNull ?: "stop",
                        )
                    )
                }
            } catch (e: Exception) {
                emit(StreamEvent.Failure("网络异常：" + (e.message ?: e.javaClass.simpleName)))
            }
            return@flow
        }

        val payload = buildJsonObject {
            put("model", settings.model)
            put("messages", messages)
            put("stream", true)
            put("temperature", settings.temperature)
            put("max_tokens", settings.maxTokens)
            if (tools != null && tools.isNotEmpty()) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
            if (settings.thinking) {
                put("chat_template_kwargs", buildJsonObject { put("enable_thinking", true) })
            }
        }

        val req = Request.Builder()
            .url(endpoint("chat/completions"))
            .header("Authorization", "Bearer " + settings.apiKey.trim())
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        var promptTokens = 0
        var completionTokens = 0
        var finish = ""

        try {
            Http.client().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = try {
                        resp.body?.string().orEmpty()
                    } catch (e: Exception) {
                        ""
                    }
                    emit(StreamEvent.Failure(describeError(resp.code, err)))
                    return@use
                }
                val source = resp.body?.source()
                if (source == null) {
                    emit(StreamEvent.Failure("服务端返回了空响应体"))
                    return@use
                }
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) continue
                    if (!line.startsWith("data:")) continue
                    val data = line.substring(5).trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break

                    val obj = try {
                        json.parseToJsonElement(data).jsonObject
                    } catch (e: Exception) {
                        continue
                    }

                    // usage 可能出现在任意 chunk
                    obj["usage"]?.let { u ->
                        if (u is JsonObject) {
                            promptTokens = u["prompt_tokens"]?.jsonPrimitive?.intOrNull
                                ?: u["input_tokens"]?.jsonPrimitive?.intOrNull ?: promptTokens
                            completionTokens = u["completion_tokens"]?.jsonPrimitive?.intOrNull
                                ?: u["output_tokens"]?.jsonPrimitive?.intOrNull ?: completionTokens
                        }
                    }

                    val choices = obj["choices"] as? JsonArray ?: continue
                    val ch = choices.firstOrNull()?.jsonObject ?: continue
                    ch["finish_reason"]?.jsonPrimitive?.contentOrNull?.let { fr ->
                        if (fr.isNotEmpty() && fr != "null") finish = fr
                    }
                    val delta = ch["delta"] as? JsonObject ?: continue

                    delta["content"]?.jsonPrimitive?.contentOrNull?.let { t ->
                        if (t.isNotEmpty()) emit(StreamEvent.Text(t))
                    }
                    val tcs = delta["tool_calls"] as? JsonArray
                    if (tcs != null) {
                        for (el in tcs) {
                            val o = el as? JsonObject ?: continue
                            val idx = o["index"]?.jsonPrimitive?.intOrNull ?: 0
                            val id = o["id"]?.jsonPrimitive?.contentOrNull
                            val fn = o["function"] as? JsonObject
                            val name = fn?.get("name")?.jsonPrimitive?.contentOrNull
                            val args = fn?.get("arguments")?.jsonPrimitive?.contentOrNull
                            emit(StreamEvent.ToolCallDelta(idx, id, name, args))
                        }
                    }
                }
            }
            emit(StreamEvent.Completed(promptTokens, completionTokens, finish))
        } catch (e: Exception) {
            emit(StreamEvent.Failure("网络异常：" + (e.message ?: e.javaClass.simpleName)))
        }
    }.flowOn(Dispatchers.IO)

    private fun describeError(code: Int, body: String): String {
        val hint = when (code) {
            401 -> "API Key 无效或没权限"
            403 -> "被拒绝了，检查 Key 权限或额度"
            404 -> "路径不对，检查 Base URL（要带 /v1）"
            429 -> "请求太频繁，Agnes 有 RPM 限制，等一会儿再搜"
            500, 502, 503, 504 -> "服务端错误，稍后重试"
            else -> "HTTP $code"
        }
        val brief = body.take(400).replace("\n", " ")
        return "请求失败（$hint）：$brief"
    }

    /** 连通性测试：先看 /models，失败再发一条最小 chat。 */
    suspend fun testConnection(): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (settings.apiKey.isBlank()) return@withContext "❌ 先填 API Key"
        try {
            val req = Request.Builder()
                .url(endpoint("models"))
                .header("Authorization", "Bearer " + settings.apiKey.trim())
                .get()
                .build()
            Http.client().newCall(req).execute().use { resp ->
                val text = try { resp.body?.string().orEmpty() } catch (e: Exception) { "" }
                if (resp.isSuccessful) {
                    val names = try {
                        json.parseToJsonElement(text).jsonObject["data"]
                            ?.let { it as? JsonArray }?.mapNotNull {
                                (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
                            } ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                    val has = names.contains(settings.model)
                    val tail = if (names.isEmpty()) "" else "\n可用模型：" + names.take(12).joinToString(", ")
                    return@withContext if (has) "✅ 连接成功，模型 ${settings.model} 在线" + tail
                    else "✅ 连接成功（当前模型 ${settings.model} 未在列表中，可能仍可用）" + tail
                }
                "❌ " + describeError(resp.code, text)
            }
        } catch (e: Exception) {
            "❌ 网络异常：" + (e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        /** 供 UI 预览用 */
        fun textMessage(role: String, text: String): JsonObject = buildJsonObject {
            put("role", role)
            put("content", text)
        }

        fun userMessages(text: String): JsonArray = buildJsonArray {
            add(textMessage("user", text) as kotlinx.serialization.json.JsonElement)
        }
    }
}
