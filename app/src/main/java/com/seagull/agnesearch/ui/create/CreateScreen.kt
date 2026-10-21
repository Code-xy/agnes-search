package com.seagull.agnesearch.ui.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seagull.agnesearch.core.AppSettings
import com.seagull.agnesearch.core.Creation
import com.seagull.agnesearch.core.Store
import com.seagull.agnesearch.ui.components.FieldLabel
import com.seagull.agnesearch.ui.components.GlassCard
import com.seagull.agnesearch.ui.components.LoadingDots
import com.seagull.agnesearch.ui.components.Pill
import com.seagull.agnesearch.ui.components.RemoteImage
import com.seagull.agnesearch.ui.components.SectionTitle
import com.seagull.agnesearch.ui.components.TextInput
import com.seagull.agnesearch.ui.components.ThinDivider
import com.seagull.agnesearch.util.ImagePicker
import com.seagull.agnesearch.ui.theme.brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CreateScreen(
    vm: CreateViewModel,
    onOpenSettings: () -> Unit,
) {
    val br = brand
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("image") }
    var viewing by remember { mutableStateOf<Creation?>(null) }

    // 一个选择器伺候所有入口
    var onPicked by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val target = onPicked
            scope.launch {
                val data = withContext(Dispatchers.IO) { ImagePicker.toDataUri(ctx, uri) }
                if (data != null) target?.invoke(data)
            }
        }
    }

    fun pick(handler: (String) -> Unit) {
        onPicked = handler
        picker.launch("image/*")
    }

    val creations by Store.creations.collectAsState()
    val images = creations.filter { it.kind == "image" }
    val videos = creations.filter { it.kind == "video" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("创作", color = br.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("图像生成 / 编辑 · 视频生成", color = br.textSecondary, fontSize = 11.sp)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置", tint = br.textSecondary)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("图像", tab == "image") { tab = "image" }
                Pill("视频", tab == "video") { tab = "video" }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (tab == "image") {
            item { ImagePanel(vm, images.firstOrNull(), ::pick) { viewing = it } }
        } else {
            item { VideoPanel(vm, videos, ::pick) }
        }

        item {
            val list = if (tab == "image") images else videos
            if (list.isNotEmpty()) {
                SectionTitle(
                    text = if (tab == "image") "最近生成" else "生成任务",
                    trailing = list.size.toString() + " 条",
                )
                for (row in list.take(20).chunked(2)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (c in row) {
                            CreationCell(
                                c = c,
                                modifier = Modifier.weight(1f),
                                onOpen = { viewing = c },
                                onDelete = { Store.deleteCreation(c.id) },
                                onRefresh = { vm.refreshVideo(c.id) },
                                onCancel = { vm.cancelVideo(c.id) },
                                onSaveVideo = { vm.saveVideoToGallery(c.id) },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    val v = viewing
    if (v != null) {
        ImageViewerDialog(c = v, onClose = { viewing = null })
    }
}

// ── 图像面板 ────────────────────────────────────────────────────────────

@Composable
private fun ImagePanel(
    vm: CreateViewModel,
    last: Creation?,
    pick: ((String) -> Unit) -> Unit,
    onOpen: (Creation) -> Unit,
) {
    val br = brand
    val st = vm.image
    val cfg by Store.settings.collectAsState()

    GlassCard(corner = 20, padding = 14) {
        Column {
            TextInput(
                value = st.prompt,
                onValueChange = { v -> vm.setImage { it.copy(prompt = v) } },
                placeholder = "描述你想要的画面，越具体越好",
                singleLine = false,
                maxLines = 6,
            )

            // 参考图
            FieldLabel("参考图（可多张，图生图 / 多图合成）", "最多 4 张，自动压成 Data URI 发给接口")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((i, r) in st.refs.withIndex()) {
                    Box(modifier = Modifier.size(64.dp)) {
                        RemoteImage(
                            src = r,
                            modifier = Modifier.size(64.dp).background(br.codeBg, RoundedCornerShape(12.dp)),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                                .background(br.card, CircleShape)
                                .clickable {
                                    vm.setImage { it.copy(refs = it.refs.filterIndexed { idx, _ -> idx != i }) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "移除",
                                tint = br.textSecondary,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
                if (st.refs.size < 4) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(br.chipBg, RoundedCornerShape(12.dp))
                            .clickable { pick { data -> vm.addRef(data) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "添加", tint = br.chipText)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    TextInput(
                        value = st.refUrl,
                        onValueChange = { v -> vm.setImage { it.copy(refUrl = v) } },
                        placeholder = "或粘贴图片 URL",
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(br.chipBg, RoundedCornerShape(14.dp))
                        .clickable {
                            val u = st.refUrl.trim()
                            if (u.startsWith("http")) {
                                vm.addRef(u)
                                vm.setImage { it.copy(refUrl = "") }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text("加入", color = br.chipText, fontSize = 13.sp)
                }
            }

            FieldLabel("尺寸档位")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in AppSettings.IMAGE_SIZES) {
                    Pill(s, st.size == s) { vm.setImage { it.copy(size = s) } }
                }
            }
            FieldLabel("画幅比例")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in AppSettings.IMAGE_RATIOS.take(5)) {
                    Pill(r, st.ratio == r) { vm.setImage { it.copy(ratio = r) } }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in AppSettings.IMAGE_RATIOS.drop(5)) {
                    Pill(r, st.ratio == r) { vm.setImage { it.copy(ratio = r) } }
                }
            }

            FieldLabel("模型")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in AppSettings.IMAGE_MODELS) {
                    Pill(
                        m.removePrefix("agnes-image-").removeSuffix("-flash"),
                        st.model == m,
                    ) { vm.setImage { it.copy(model = m) } }
                }
            }

            if (st.error.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(st.error, color = br.danger, fontSize = 12.sp)
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(Brush.horizontalGradient(br.gradient), RoundedCornerShape(16.dp))
                        .clickable(enabled = !st.running) { vm.generateImage() }
                        .padding(horizontal = 22.dp, vertical = 13.dp),
                ) {
                    Text(
                        if (st.running) "生成中…" else "开始生成",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
                if (st.running) {
                    Spacer(Modifier.width(14.dp))
                    LoadingDots()
                    Spacer(Modifier.width(10.dp))
                    Text("4K 可能要一两分钟", color = br.textSecondary, fontSize = 11.sp)
                }
            }
        }
    }

    if (last != null && last.status == "done" && !st.running) {
        Spacer(Modifier.height(14.dp))
        GlassCard(corner = 20, padding = 10, onClick = { onOpen(last) }) {
            Column {
                RemoteImage(
                    src = if (last.localPath.isNotBlank()) last.localPath else last.url,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(br.codeBg, RoundedCornerShape(14.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    last.prompt,
                    color = br.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "点开大图 · 长按不了没关系，弹窗里有保存和分享",
                    color = br.textSecondary,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

// ── 生成结果格子 ────────────────────────────────────────────────────────

@Composable
fun CreationCell(
    c: Creation,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onCancel: () -> Unit,
    onSaveVideo: () -> Unit,
) {
    val br = brand
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .background(br.card, shape)
            .border(1.dp, br.cardBorder, shape)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(br.codeBg, RoundedCornerShape(12.dp))
                .clickable { if (c.status == "done") onOpen() },
            contentAlignment = Alignment.Center,
        ) {
            when {
                c.status == "done" && c.kind == "image" -> RemoteImage(
                    src = if (c.localPath.isNotBlank()) c.localPath else c.url,
                    modifier = Modifier.fillMaxSize(),
                )

                c.status == "done" && c.kind == "video" -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Brush.linearGradient(br.gradient), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "播放", tint = Color.White)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("点开播放", color = br.textSecondary, fontSize = 10.sp)
                }

                c.status == "failed" -> Text(
                    "失败",
                    color = br.danger,
                    fontSize = 12.sp,
                )

                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingDots()
                    Spacer(Modifier.height(6.dp))
                    Text(c.progress.toString() + "%", color = br.accent, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            c.prompt.ifBlank { "（无提示词）" },
            color = br.textPrimary,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            buildString {
                append(c.model)
                if (c.size.isNotBlank()) append(" · ").append(c.size)
                if (c.seconds.isNotBlank()) append(" · ").append(c.seconds).append("s")
            },
            color = br.textSecondary,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(4.dp))
        ThinDivider()
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (c.status == "queued" || c.status == "running" || c.status == "in_progress") {
                SmallAction("刷新") { onRefresh() }
                SmallAction("取消") { onCancel() }
            } else if (c.kind == "video" && c.status == "done") {
                SmallAction("保存") { onSaveVideo() }
                SmallAction("打开") { onOpen() }
            } else {
                SmallAction("打开") { onOpen() }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onDelete() }, modifier = Modifier.size(26.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = br.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    val br = brand
    Box(
        modifier = Modifier
            .background(br.chipBg, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(label, color = br.chipText, fontSize = 10.sp)
    }
}
