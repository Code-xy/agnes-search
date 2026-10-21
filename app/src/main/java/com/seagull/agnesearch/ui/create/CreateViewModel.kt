package com.seagull.agnesearch.ui.create

import android.app.Application
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seagull.agnesearch.core.AppSettings
import com.seagull.agnesearch.core.Creation
import com.seagull.agnesearch.core.Store
import com.seagull.agnesearch.net.MediaClient
import com.seagull.agnesearch.net.MediaResult
import com.seagull.agnesearch.util.MediaSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ImageUi(
    val prompt: String = "",
    val size: String = "1K",
    val ratio: String = "1:1",
    val model: String = "agnes-image-2.5-flash",
    val refs: List<String> = emptyList(),
    val refUrl: String = "",
    val running: Boolean = false,
    val status: String = "",
    val error: String = "",
    val lastId: String = "",
)

data class VideoUi(
    val prompt: String = "",
    val mode: String = "text",
    val size: String = "720P",
    val aspect: String = "16:9",
    val seconds: String = "5",
    val model: String = "agnes-video-2.5",
    val firstFrame: String = "",
    val lastFrame: String = "",
    val refs: List<String> = emptyList(),
    val refUrl: String = "",
    val running: Boolean = false,
    val error: String = "",
)

class CreateViewModel(app: Application) : AndroidViewModel(app) {

    var image by mutableStateOf(ImageUi())
        private set

    var video by mutableStateOf(VideoUi())
        private set

    private var imageJob: Job? = null
    private val videoJobs = HashMap<String, Job>()

    init {
        // 默认参数取设置里的值
        val cfg = Store.settings.value
        image = image.copy(model = cfg.imageModel, size = cfg.imageSize, ratio = cfg.imageRatio)
        video = video.copy(
            model = cfg.videoModel,
            size = cfg.videoSize,
            aspect = cfg.videoAspect,
            seconds = cfg.videoSeconds,
        )
    }

    // ── 图像 ────────────────────────────────────────────────────────────

    fun setImage(block: (ImageUi) -> ImageUi) {
        image = block(image)
    }

    fun setVideo(block: (VideoUi) -> VideoUi) {
        video = block(video)
    }

    fun addRef(uri: String) {
        if (uri.isBlank()) return
        image = image.copy(refs = (image.refs + uri).take(4))
    }

    fun addVideoRef(uri: String) {
        if (uri.isBlank()) return
        video = video.copy(refs = (video.refs + uri).take(6))
    }

    fun generateImage() {
        val cfg = Store.settings.value
        val p = image.prompt.trim()
        if (p.isEmpty()) {
            image = image.copy(error = "先写一句画面描述")
            return
        }
        if (cfg.apiKey.isBlank()) {
            image = image.copy(error = "还没填 API Key，去「我的 → 接口设置」")
            return
        }
        imageJob?.cancel()
        image = image.copy(running = true, error = "", status = "正在生成…")

        val req = image
        val started = System.currentTimeMillis()

        imageJob = viewModelScope.launch {
            val client = MediaClient(cfg)
            when (val r = client.generateImage(
                prompt = p,
                size = req.size,
                ratio = req.ratio,
                model = req.model,
                refImages = req.refs,
                wantBase64 = false,
            )) {
                is MediaResult.Err -> {
                    image = image.copy(running = false, error = r.message, status = "")
                }

                is MediaResult.Ok -> {
                    val out = r.value
                    val id = Store.newId()
                    var local = ""
                    var bytes: ByteArray? = null
                    if (out.url.isNotBlank()) {
                        bytes = client.download(out.url)
                    } else if (out.b64.isNotBlank()) {
                        bytes = try {
                            Base64.decode(out.b64, Base64.DEFAULT)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (bytes != null) {
                        local = MediaSaver.saveLocal(getApplication(), id, "jpg", bytes).orEmpty()
                        if (cfg.autoSaveImage) {
                            MediaSaver.saveImage(getApplication(), bytes)
                        }
                    }
                    val creation = Creation(
                        id = id,
                        kind = "image",
                        prompt = p,
                        model = req.model,
                        status = "done",
                        url = out.url,
                        localPath = local,
                        size = req.size,
                        ratio = req.ratio,
                        createdAt = System.currentTimeMillis(),
                        elapsedMs = System.currentTimeMillis() - started,
                    )
                    Store.addCreation(creation)
                    Store.bumpImages()
                    image = image.copy(running = false, status = "", error = "", lastId = id)
                }
            }
        }
    }

    fun stopImage() {
        imageJob?.cancel()
        image = image.copy(running = false, status = "")
    }

    // ── 视频 ────────────────────────────────────────────────────────────

    fun generateVideo() {
        val cfg = Store.settings.value
        val req = video
        val p = req.prompt.trim()
        if (p.isEmpty()) {
            video = video.copy(error = "先写一句视频描述")
            return
        }
        if (cfg.apiKey.isBlank()) {
            video = video.copy(error = "还没填 API Key，去「我的 → 接口设置」")
            return
        }
        if (req.mode == "keyframe" && req.firstFrame.isBlank() && req.lastFrame.isBlank()) {
            video = video.copy(error = "首尾帧模式至少要给一张首帧或尾帧图片")
            return
        }
        if (req.mode == "reference" && req.refs.isEmpty()) {
            video = video.copy(error = "参考模式至少要给一张参考图")
            return
        }

        video = video.copy(running = true, error = "")

        viewModelScope.launch {
            val client = MediaClient(cfg)
            when (val r = client.createVideo(
                prompt = p,
                mode = req.mode,
                model = req.model,
                seconds = req.seconds,
                size = req.size,
                aspect = req.aspect,
                firstFrame = req.firstFrame,
                lastFrame = req.lastFrame,
                images = req.refs,
                seed = null,
            )) {
                is MediaResult.Err -> {
                    video = video.copy(running = false, error = r.message)
                }

                is MediaResult.Ok -> {
                    val t = r.value
                    val creation = Creation(
                        id = Store.newId(),
                        kind = "video",
                        prompt = p,
                        model = req.model,
                        status = "queued",
                        size = req.size,
                        ratio = req.aspect,
                        seconds = req.seconds,
                        mode = req.mode,
                        progress = t.progress,
                        createdAt = System.currentTimeMillis(),
                        videoId = t.videoId,
                    )
                    Store.addCreation(creation)
                    Store.bumpVideos()
                    video = video.copy(running = false, error = "")
                    pollVideo(creation.id, t.videoId, req.model)
                }
            }
        }
    }

    /** 每 2 秒轮询一次，最多 12 分钟。 */
    private fun pollVideo(creationId: String, videoId: String, model: String) {
        val cfg = Store.settings.value
        val job = viewModelScope.launch {
            val client = MediaClient(cfg)
            var tries = 0
            var netFails = 0
            val started = System.currentTimeMillis()
            while (tries < 360) {
                delay(2000)
                tries++
                when (val r = client.queryVideo(videoId, model)) {
                    is MediaResult.Err -> {
                        netFails++
                        Store.updateCreation(creationId) {
                            it.copy(error = "查询失败 " + netFails + " 次：" + r.message)
                        }
                        if (netFails >= 8) break
                    }

                    is MediaResult.Ok -> {
                        netFails = 0
                        val st = r.value
                        val status = st.status.ifEmpty { "in_progress" }
                        Store.updateCreation(creationId) {
                            it.copy(
                                status = status,
                                progress = st.progress,
                                url = if (st.url.isNotBlank()) st.url else it.url,
                                error = st.error,
                                elapsedMs = System.currentTimeMillis() - started,
                            )
                        }
                        if (status == "completed") {
                            if (st.url.isNotBlank() && cfg.autoSaveVideo) {
                                client.download(st.url)?.let { bytes ->
                                    MediaSaver.saveVideo(getApplication(), bytes)
                                }
                            }
                            break
                        }
                        if (status == "failed") break
                    }
                }
            }
            videoJobs.remove(creationId)
        }
        videoJobs[creationId] = job
    }

    fun cancelVideo(creationId: String) {
        videoJobs.remove(creationId)?.cancel()
        Store.updateCreation(creationId) { it.copy(status = "failed", error = "已取消轮询") }
    }

    /** 手动查一次（点刷新）。 */
    fun refreshVideo(creationId: String) {
        val c = Store.creations.value.firstOrNull { it.id == creationId } ?: return
        if (c.videoId.isBlank()) return
        viewModelScope.launch {
            val client = MediaClient(Store.settings.value)
            when (val r = client.queryVideo(c.videoId, c.model)) {
                is MediaResult.Ok -> Store.updateCreation(creationId) {
                    it.copy(
                        status = r.value.status.ifEmpty { it.status },
                        progress = r.value.progress,
                        url = if (r.value.url.isNotBlank()) r.value.url else it.url,
                        error = r.value.error,
                    )
                }

                is MediaResult.Err -> Store.updateCreation(creationId) { it.copy(error = r.message) }
            }
        }
    }

    fun saveVideoToGallery(creationId: String) {
        val c = Store.creations.value.firstOrNull { it.id == creationId } ?: return
        if (c.url.isBlank()) return
        viewModelScope.launch {
            try {
                val client = MediaClient(Store.settings.value)
                val bytes = client.download(c.url) ?: return@launch
                val where = MediaSaver.saveVideo(getApplication(), bytes)
                Store.updateCreation(creationId) {
                    it.copy(error = if (where != null) "" else it.error)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 忽略
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        videoJobs.values.forEach { it.cancel() }
        videoJobs.clear()
    }

    companion object {
        val DEFAULT = AppSettings()
    }
}
