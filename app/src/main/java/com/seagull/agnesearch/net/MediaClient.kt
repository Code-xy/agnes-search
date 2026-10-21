package com.seagull.agnesearch.net

import com.seagull.agnesearch.core.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

sealed class MediaResult<out T> {
    data class Ok<T>(val value: T) : MediaResult<T>()
    data class Err(val message: String) : MediaResult<Nothing>()
}

data class ImageOut(
    val url: String = "",
    val b64: String = "",
    val revisedPrompt: String = "",
)

data class VideoTask(
    val videoId: String = "",
    val taskId: String = "",
    val status: String = "",
    val progress: Int = 0,
)

data class VideoStatus(
    val status: String = "",
    val progress: Int = 0,
    val url: String = "",
    val error: String = "",
    val seconds: String = "",
    val size: String = "",
)

/**
 * Agnes 图像 / 视频接口客户端。
 *
 *  图像：  POST {base}/images/generations          同步返回 url / b64_json
 *  视频：  POST {base}/videos                      异步任务，返回 video_id
 *          GET  {root}/agnesapi?video_id=..&model_name=..   轮询进度与结果
 *
 * 注意视频查询路径在站点根上，不在 /v1 下。
 */
class MediaClient(private val settings: AppSettings) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun base(): String {
        var b = settings.baseUrl.trim()
        if (b.isEmpty()) b = AppSettings.DEFAULT_BASE_URL
        b = b.trimEnd('/')
        if (!b.endsWith("/v1") && !b.contains("/v1/")) b = "$b/v1"
        return b
    }

    private fun root(): String {
        var b = base()
        if (b.endsWith("/v1")) b = b.substring(0, b.length - 3)
        else b = b.substringBefore("/v1/")
        return b.trimEnd('/')
    }

    // ── 图像 ────────────────────────────────────────────────────────────

    suspend fun generateImage(
        prompt: String,
        size: String,
        ratio: String,
        model: String,
        refImages: List<String>,
        wantBase64: Boolean,
    ): MediaResult<ImageOut> = withContext(Dispatchers.IO) {
        if (settings.apiKey.isBlank()) return@withContext MediaResult.Err("还没填 API Key")

        val legacy = model.contains("2.0")
        val body = buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            put("size", if (legacy) legacySize(ratio, size) else size)
            if (!legacy) put("ratio", ratio)
            put(
                "extra_body",
                buildJsonObject {
                    if (refImages.isNotEmpty()) {
                        put(
                            "image",
                            buildJsonArray {
                                for (u in refImages.take(4)) add(JsonPrimitive(u))
                            }
                        )
                    }
                    put("response_format", if (wantBase64) "b64_json" else "url")
                }
            )
        }

        val req = Request.Builder()
            .url(base() + "/images/generations")
            .header("Authorization", "Bearer " + settings.apiKey.trim())
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(Http.JSON_MEDIA))
            .build()

        try {
            Http.client().newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext MediaResult.Err(httpMsg(resp.code, text))
                parseImage(text, wantBase64)
            }
        } catch (e: Exception) {
            MediaResult.Err("网络异常：" + (e.message ?: e.javaClass.simpleName))
        }
    }

    private fun parseImage(text: String, wantBase64: Boolean): MediaResult<ImageOut> {
        return try {
            val o = json.parseToJsonElement(text).jsonObject
            o["error"]?.let { err ->
                val m = if (err is JsonObject) {
                    err["message"]?.jsonPrimitive?.contentOrNull
                } else {
                    err.jsonPrimitive.contentOrNull
                }
                if (!m.isNullOrBlank()) return MediaResult.Err(m)
            }
            val arr = o["data"] as? JsonArray
            val first = arr?.firstOrNull() as? JsonObject
            val url = first?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
            val b64 = first?.get("b64_json")?.jsonPrimitive?.contentOrNull.orEmpty()
            val revised = first?.get("revised_prompt")?.jsonPrimitive?.contentOrNull.orEmpty()
            if (url.isBlank() && b64.isBlank()) {
                MediaResult.Err("服务端没返回图片数据：" + text.take(200))
            } else {
                MediaResult.Ok(ImageOut(url, b64, revised))
            }
        } catch (e: Exception) {
            MediaResult.Err("解析响应失败：" + text.take(200))
        }
    }

    private fun legacySize(ratio: String, tier: String): String {
        return when (ratio) {
            "4:3" -> "1024x768"
            "3:4" -> "768x1024"
            "16:9", "3:2", "21:9" -> "1024x576"
            "9:16", "2:3" -> "576x1024"
            else -> if (tier == "1K") "1024x1024" else "1024x1024"
        }
    }

    // ── 视频 ────────────────────────────────────────────────────────────

    suspend fun createVideo(
        prompt: String,
        mode: String,
        model: String,
        seconds: String,
        size: String,
        aspect: String,
        firstFrame: String,
        lastFrame: String,
        images: List<String>,
        seed: Int?,
    ): MediaResult<VideoTask> = withContext(Dispatchers.IO) {
        if (settings.apiKey.isBlank()) return@withContext MediaResult.Err("还没填 API Key")

        val body = buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            put("mode", mode)
            put("seconds", seconds)
            put("size", size)
            if (mode == "keyframe") {
                if (firstFrame.isNotBlank()) put("first_frame", firstFrame)
                if (lastFrame.isNotBlank()) put("last_frame", lastFrame)
            }
            if (mode == "reference") {
                put("aspect_ratio", aspect)
                if (images.isNotEmpty()) {
                    put(
                        "images",
                        buildJsonArray {
                            for (u in images.take(12)) add(JsonPrimitive(u))
                        }
                    )
                }
            } else {
                put("aspect_ratio", aspect)
            }
            if (seed != null) put("seed", seed)
        }

        val req = Request.Builder()
            .url(base() + "/videos")
            .header("Authorization", "Bearer " + settings.apiKey.trim())
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(Http.JSON_MEDIA))
            .build()

        try {
            Http.client().newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext MediaResult.Err(httpMsg(resp.code, text))
                val o = json.parseToJsonElement(text).jsonObject
                val vid = o["video_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (vid.isBlank()) {
                    return@withContext MediaResult.Err("没拿到 video_id：" + text.take(200))
                }
                MediaResult.Ok(
                    VideoTask(
                        videoId = vid,
                        taskId = o["task_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        status = o["status"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        progress = o["progress"]?.jsonPrimitive?.intOrNull ?: 0,
                    )
                )
            }
        } catch (e: Exception) {
            MediaResult.Err("网络异常：" + (e.message ?: e.javaClass.simpleName))
        }
    }

    suspend fun queryVideo(videoId: String, model: String): MediaResult<VideoStatus> =
        withContext(Dispatchers.IO) {
            val url = root() + "/agnesapi?video_id=" + videoId + "&model_name=" + model
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + settings.apiKey.trim())
                .header("Accept", "application/json")
                .get()
                .build()
            try {
                Http.client().newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) return@withContext MediaResult.Err(httpMsg(resp.code, text))
                    val o = json.parseToJsonElement(text).jsonObject
                    val err = o["error"]
                    val errText = when (err) {
                        null -> ""
                        is JsonObject -> err["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        else -> err.jsonPrimitive.contentOrNull.orEmpty()
                    }
                    MediaResult.Ok(
                        VideoStatus(
                            status = o["status"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            progress = o["progress"]?.jsonPrimitive?.intOrNull ?: 0,
                            url = o["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            error = errText,
                            seconds = o["seconds"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            size = o["size"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        )
                    )
                }
            } catch (e: Exception) {
                MediaResult.Err("网络异常：" + (e.message ?: e.javaClass.simpleName))
            }
        }

    /** 下载图片 / 视频字节，用于本地保存。 */
    suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).header("User-Agent", Http.randomUa()).get().build()
            Http.client().newCall(req).execute().use { resp: Response ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.bytes()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun httpMsg(code: Int, body: String): String {
        val hint = when (code) {
            401 -> "API Key 无效"
            403 -> "无权限或额度不足"
            404 -> "接口路径不对，检查 Base URL"
            429 -> "太频繁了，Agnes 有 RPM 限制，等会儿再试"
            500, 502, 503, 504 -> "服务端错误"
            else -> "HTTP $code"
        }
        return "$hint：${body.take(300).replace("\\n", " ")}"
    }
}
