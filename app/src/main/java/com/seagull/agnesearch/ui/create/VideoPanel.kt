package com.seagull.agnesearch.ui.create

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.seagull.agnesearch.core.AppSettings
import com.seagull.agnesearch.core.Creation
import com.seagull.agnesearch.core.Store
import com.seagull.agnesearch.net.MediaClient
import com.seagull.agnesearch.ui.components.FieldLabel
import com.seagull.agnesearch.ui.components.GlassCard
import com.seagull.agnesearch.ui.components.LoadingDots
import com.seagull.agnesearch.ui.components.Pill
import com.seagull.agnesearch.ui.components.RemoteImage
import com.seagull.agnesearch.ui.components.TextInput
import com.seagull.agnesearch.ui.theme.brand
import com.seagull.agnesearch.util.MediaSaver
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun VideoPanel(
    vm: CreateViewModel,
    videos: List<Creation>,
    pick: ((String) -> Unit) -> Unit,
) {
    val br = brand
    val st = vm.video

    GlassCard(corner = 20, padding = 14) {
        Column {
            TextInput(
                value = st.prompt,
                onValueChange = { v -> vm.setVideo { it.copy(prompt = v) } },
                placeholder = "描述画面、动作、镜头和声音",
                singleLine = false,
                maxLines = 6,
            )

            FieldLabel("生成模式")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("文生视频", st.mode == "text") { vm.setVideo { it.copy(mode = "text") } }
                Pill("首尾帧", st.mode == "keyframe") { vm.setVideo { it.copy(mode = "keyframe") } }
                Pill("图参考", st.mode == "reference") { vm.setVideo { it.copy(mode = "reference") } }
            }

            if (st.mode == "keyframe") {
                FieldLabel("首帧图片", "URL；也可从相册选（会转成 Data URI）")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        TextInput(
                            value = st.firstFrame,
                            onValueChange = { v -> vm.setVideo { it.copy(firstFrame = v) } },
                            placeholder = "https://…/first.png",
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    PickButton { pick { d -> vm.setVideo { it.copy(firstFrame = d) } } }
                }
                FieldLabel("尾帧图片（可只给一张）")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        TextInput(
                            value = st.lastFrame,
                            onValueChange = { v -> vm.setVideo { it.copy(lastFrame = v) } },
                            placeholder = "https://…/last.png",
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    PickButton { pick { d -> vm.setVideo { it.copy(lastFrame = d) } } }
                }
            }

            if (st.mode == "reference") {
                FieldLabel("参考图", "提示词里可用 <Picture 1> 指代")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((i, r) in st.refs.withIndex()) {
                        Box(modifier = Modifier.size(56.dp)) {
                            RemoteImage(src = r, modifier = Modifier.size(56.dp))
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .background(br.card, CircleShape)
                                    .clickable {
                                        vm.setVideo { it.copy(refs = it.refs.filterIndexed { idx, _ -> idx != i }) }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = br.textSecondary,
                                    modifier = Modifier.size(11.dp),
                                )
                            }
                        }
                    }
                    if (st.refs.size < 6) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(br.chipBg, RoundedCornerShape(12.dp))
                                .clickable { pick { d -> vm.addVideoRef(d) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "添加", tint = br.chipText)
                        }
                    }
                }
            }

            FieldLabel("时长")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in AppSettings.VIDEO_SECONDS) {
                    Pill(s + "s", st.seconds == s) { vm.setVideo { it.copy(seconds = s) } }
                }
            }

            FieldLabel("分辨率")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in AppSettings.VIDEO_SIZES) {
                    Pill(s, st.size == s) { vm.setVideo { it.copy(size = s) } }
                }
            }

            FieldLabel("画幅")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in AppSettings.VIDEO_ASPECTS.take(4)) {
                    Pill(a, st.aspect == a) { vm.setVideo { it.copy(aspect = a) } }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in AppSettings.VIDEO_ASPECTS.drop(4)) {
                    Pill(a, st.aspect == a) { vm.setVideo { it.copy(aspect = a) } }
                }
            }

            FieldLabel("模型")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in AppSettings.VIDEO_MODELS) {
                    Pill(m.removePrefix("agnes-video-"), st.model == m) {
                        vm.setVideo { it.copy(model = m) }
                    }
                }
            }

            if (st.error.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(st.error, color = br.danger, fontSize = 12.sp)
            }

            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .background(Brush.horizontalGradient(br.gradient), RoundedCornerShape(16.dp))
                    .clickable(enabled = !st.running) { vm.generateVideo() }
                    .padding(horizontal = 22.dp, vertical = 13.dp),
            ) {
                Text(
                    if (st.running) "提交中…" else "生成视频",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "视频是异步任务：提交后进下方任务列表轮询进度，完成后可播放或保存到相册。",
                color = br.textSecondary,
                fontSize = 10.sp,
            )
        }
    }

    val running = videos.filter { it.status == "queued" || it.status == "running" || it.status == "in_progress" }
    if (running.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        GlassCard(corner = 18, padding = 14) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingDots()
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "正在生成 " + running.size + " 个任务",
                        color = br.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(10.dp))
                for (c in running) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                c.prompt,
                                color = br.textPrimary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(br.divider, CircleShape),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth((c.progress.coerceIn(0, 100) / 100f))
                                        .height(3.dp)
                                        .background(Brush.horizontalGradient(br.gradient), CircleShape),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            c.progress.toString() + "%",
                            color = br.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickButton(onClick: () -> Unit) {
    val br = brand
    Box(
        modifier = Modifier
            .background(br.chipBg, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text("图片", color = br.chipText, fontSize = 13.sp)
    }
}

/** 生成结果弹窗：看图 / 保存 / 分享 / 播放。 */
@Composable
fun ImageViewerDialog(c: Creation, onClose: () -> Unit) {
    val br = brand
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onClose) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(br.card, RoundedCornerShape(20.dp))
                .padding(14.dp),
        ) {
            Column {
                if (c.kind == "image") {
                    RemoteImage(
                        src = if (c.localPath.isNotBlank()) c.localPath else c.url,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(br.codeBg, RoundedCornerShape(14.dp))
                            .clickable {
                                if (c.url.isNotBlank()) uri.openUri(c.url)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Brush.linearGradient(br.gradient), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "播放",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (c.url.isBlank()) "还没有可播放的地址" else "点这里用系统播放器打开",
                                color = br.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    c.prompt,
                    color = br.textPrimary,
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append(c.model)
                        if (c.size.isNotBlank()) append(" · ").append(c.size)
                        if (c.ratio.isNotBlank()) append(" · ").append(c.ratio)
                        if (c.seconds.isNotBlank()) append(" · ").append(c.seconds).append("s")
                        if (c.elapsedMs > 0) append(" · ").append(String.format("%.1fs", c.elapsedMs / 1000.0))
                    },
                    color = br.textSecondary,
                    fontSize = 10.sp,
                )

                if (msg.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(msg, color = br.accent, fontSize = 11.sp)
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogAction("保存到相册") {
                        scope.launch {
                            val bytes: ByteArray? = if (c.kind == "image" && c.localPath.isNotBlank()) {
                                try {
                                    File(c.localPath).readBytes()
                                } catch (e: Exception) {
                                    null
                                }
                            } else if (c.kind == "video") {
                                MediaClient(Store.settings.value).download(c.url)
                            } else {
                                MediaClient(Store.settings.value).download(c.url)
                            }
                            if (bytes == null) {
                                msg = "下载失败"
                            } else {
                                val where = if (c.kind == "image") {
                                    MediaSaver.saveImage(ctx, bytes)
                                } else {
                                    MediaSaver.saveVideo(ctx, bytes)
                                }
                                msg = if (where != null) "已保存：$where" else "保存失败（可能是存储权限）"
                            }
                        }
                    }
                    DialogAction("分享") {
                        val text = if (c.url.isNotBlank()) c.url else c.prompt
                        try {
                            val i = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            ctx.startActivity(Intent.createChooser(i, "分享"))
                        } catch (e: Exception) {
                            msg = "分享失败"
                        }
                    }
                    if (c.url.isNotBlank()) {
                        DialogAction("打开原图") { uri.openUri(c.url) }
                    }
                    Spacer(Modifier.weight(1f))
                    DialogAction("关闭") { onClose() }
                }
            }
        }
    }
}

@Composable
private fun DialogAction(label: String, onClick: () -> Unit) {
    val br = brand
    Box(
        modifier = Modifier
            .background(br.chipBg, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        Text(label, color = br.chipText, fontSize = 12.sp)
    }
}
