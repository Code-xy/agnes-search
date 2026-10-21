package com.seagull.agnesearch.ui.chat

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seagull.agnesearch.core.ChatMessage
import com.seagull.agnesearch.core.Store
import com.seagull.agnesearch.ui.components.GlassCard
import com.seagull.agnesearch.ui.components.ImageSourceSheet
import com.seagull.agnesearch.ui.components.LoadingDots
import com.seagull.agnesearch.ui.components.MarkdownText
import com.seagull.agnesearch.ui.components.RemoteImage
import com.seagull.agnesearch.ui.components.rememberMediaPicker
import com.seagull.agnesearch.util.FilePicker
import com.seagull.agnesearch.ui.components.oneLine
import com.seagull.agnesearch.ui.theme.brand
import com.seagull.agnesearch.util.ImagePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onOpenSettings: () -> Unit,
) {
    val br = brand
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current

    val chats by Store.chats.collectAsState()
    val chat = chats.firstOrNull { it.id == vm.currentId }
    val messages = chat?.messages ?: emptyList()
    var showChatList by remember { mutableStateOf(false) }

    var showImgSheet by remember { mutableStateOf(false) }
    val media = rememberMediaPicker(
        maxDim = 1280,
        quality = 84,
        onImage = { d -> vm.addImage(d) },
        onFile = { a -> vm.addFile(a) },
        onNotice = { m -> vm.showNotice(m) },
    )

    val lastIndex = messages.size + (if (vm.streaming) 1 else 0)
    LaunchedEffect(lastIndex, vm.streamText.length) {
        if (lastIndex > 0) {
            try {
                listState.animateScrollToItem(lastIndex)
            } catch (e: Exception) {
                // 列表还没铺完，忽略
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── 顶部 ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.newChat() }) {
                Icon(Icons.Filled.Add, contentDescription = "新建对话", tint = br.textPrimary)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showChatList = true },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        chat?.title ?: "新对话",
                        color = br.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = br.textSecondary,
                        modifier = Modifier.size(11.dp),
                    )
                }
                Text(
                    (chat?.model ?: Store.settings.value.model) + " · " + chats.size + " 个会话",
                    color = br.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置", tint = br.textSecondary)
            }
        }

        // ── 消息流 ──
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty() && !vm.streaming) {
                EmptyChat(onPick = { vm.draft = it })
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(messages, key = { it.id }) { m ->
                        if (m.role == "user") UserBubble(m) else AssistantBubble(m, false)
                    }
                    if (vm.streaming) {
                        item(key = "streaming") {
                            AssistantBubble(
                                ChatMessage(role = "assistant", content = vm.streamText),
                                true,
                            )
                        }
                    }
                }
            }
        }

        // ── 输入区 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (vm.error.isNotEmpty()) {
                Text(
                    vm.error,
                    color = br.danger,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                )
            }
            if (vm.notice.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .background(br.chipBg, RoundedCornerShape(10.dp))
                        .clickable { vm.clearNotice() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        vm.notice,
                        color = br.chipText,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text("知道了", color = br.accent, fontSize = 11.sp)
                }
            }

            if (vm.pendingFiles.isNotEmpty()) {
                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                    for ((i, f) in vm.pendingFiles.withIndex()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 5.dp)
                                .background(br.card, RoundedCornerShape(12.dp))
                                .border(1.dp, br.cardBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(br.chipBg, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    f.name.substringAfterLast('.', "?").take(4),
                                    color = br.chipText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                            Spacer(Modifier.width(9.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    f.name,
                                    color = br.textPrimary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val sub = buildString {
                                    append(FilePicker.formatSize(f.size))
                                    if (f.kind == "text") append(" · 已读取正文 ").append(f.text.length).append(" 字")
                                    if (f.note.isNotBlank()) append(" · ").append(f.note)
                                }
                                Text(sub, color = br.textSecondary, fontSize = 9.sp, maxLines = 2)
                            }
                            IconButton(onClick = { vm.removeFile(i) }, modifier = Modifier.size(26.dp)) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "移除",
                                    tint = br.textSecondary,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (vm.pendingImages.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for ((i, img) in vm.pendingImages.withIndex()) {
                        Box(modifier = Modifier.size(52.dp)) {
                            RemoteImage(
                                src = img,
                                modifier = Modifier.size(52.dp).background(br.codeBg, RoundedCornerShape(10.dp)),
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .background(br.card, CircleShape)
                                    .clickable { vm.removeImage(i) },
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
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(br.card, RoundedCornerShape(22.dp))
                    .border(1.dp, br.cardBorder, RoundedCornerShape(22.dp))
                    .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(onClick = { showImgSheet = true }, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "加图片",
                        tint = br.textSecondary,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Box(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                    if (vm.draft.isEmpty()) {
                        Text("说点什么…", color = br.textSecondary, fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = vm.draft,
                        onValueChange = { vm.draft = it },
                        textStyle = TextStyle(color = br.textPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(br.accent),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (vm.streaming) Brush.linearGradient(listOf(br.danger, br.danger))
                            else Brush.linearGradient(br.gradient),
                            CircleShape,
                        )
                        .clickable {
                            if (vm.streaming) vm.stop() else {
                                focus.clearFocus()
                                vm.send()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (vm.streaming) Icons.Filled.Close else Icons.Filled.ArrowForward,
                        contentDescription = if (vm.streaming) "停止" else "发送",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    if (showImgSheet) {
        ImageSourceSheet(
            onCamera = {
                media.takePhoto()
                showImgSheet = false
            },
            onGallery = {
                media.pickGallery()
                showImgSheet = false
            },
            onFile = {
                media.pickFile()
                showImgSheet = false
            },
            onDismiss = { showImgSheet = false },
        )
    }

    if (showChatList) {
        ChatListSheet(
            chats = chats,
            currentId = vm.currentId,
            onPick = { id ->
                vm.open(id)
                showChatList = false
            },
            onNew = {
                vm.newChat()
                showChatList = false
            },
            onDelete = { id -> vm.deleteChat(id) },
            onClose = { showChatList = false },
        )
    }
}

@Composable
private fun EmptyChat(onPick: (String) -> Unit) {
    val br = brand
    val tips = listOf(
        "帮我写一个 Python 脚本批量重命名文件",
        "解释一下 Transformer 的注意力机制",
        "把这段话润色得更正式一些",
        "用表格对比 Rizin 和 Ghidra",
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            GlassCard(corner = 20, padding = 16) {
                Text("开始一个新对话", color = br.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "支持流式输出、多轮上下文、图片输入和 Markdown 渲染。" +
                        "会话全部存在本机，左上角 + 号开新对话，点标题切换会话。",
                    color = br.textSecondary,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        items(tips) { t ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(br.card, RoundedCornerShape(14.dp))
                    .border(1.dp, br.cardBorder, RoundedCornerShape(14.dp))
                    .clickable { onPick(t) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(t, color = br.textPrimary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun UserBubble(m: ChatMessage) {
    val br = brand
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        if (m.images.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                for (img in m.images) {
                    RemoteImage(
                        src = img,
                        modifier = Modifier.size(96.dp).background(br.codeBg, RoundedCornerShape(12.dp)),
                    )
                }
            }
        }
        if (m.content.isNotBlank()) {
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(br.gradient),
                            RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(m.content, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun AssistantBubble(m: ChatMessage, streaming: Boolean) {
    val br = brand
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        GlassCard(corner = 16, padding = 14) {
            if (streaming && m.content.isEmpty()) {
                LoadingDots()
            } else {
                MarkdownText(markdown = m.content)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!streaming) {
                Text(
                    buildString {
                        if (m.model.isNotBlank()) append(m.model)
                        if (m.elapsedMs > 0) {
                            if (isNotEmpty()) append(" · ")
                            append(String.format("%.1fs", m.elapsedMs / 1000.0))
                        }
                        if (m.completionTokens > 0) {
                            if (isNotEmpty()) append(" · ")
                            append(m.completionTokens).append(" tok")
                        }
                    },
                    color = br.textSecondary,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (copied) "已复制" else "复制",
                    color = br.accent,
                    fontSize = 10.sp,
                    modifier = Modifier.clickable {
                        clipboard.setText(AnnotatedString(m.content))
                        copied = true
                    },
                )
                if (m.error) {
                    Spacer(Modifier.width(10.dp))
                    Text("失败", color = br.danger, fontSize = 10.sp)
                }
            } else {
                LoadingDots()
                Spacer(Modifier.width(8.dp))
                Text("生成中…", color = br.textSecondary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ChatListSheet(
    chats: List<com.seagull.agnesearch.core.Chat>,
    currentId: String?,
    onPick: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    val br = brand
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(br.card, RoundedCornerShape(20.dp))
                .padding(14.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("会话", color = br.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .background(Brush.linearGradient(br.gradient), RoundedCornerShape(12.dp))
                            .clickable { onNew() }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text("新建", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (chats.isEmpty()) {
                    Text("还没有会话", color = br.textSecondary, fontSize = 12.sp)
                } else {
                    LazyColumn(modifier = Modifier.height(380.dp)) {
                        items(chats, key = { it.id }) { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (c.id == currentId) br.chipBg else br.card,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable { onPick(c.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        c.title,
                                        color = br.textPrimary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        c.messages.size.toString() + " 条 · " + oneLine(
                                            c.messages.lastOrNull()?.content ?: "", 40
                                        ),
                                        color = br.textSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { onDelete(c.id) }, modifier = Modifier.size(30.dp)) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "删除",
                                        tint = br.textSecondary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(
                        modifier = Modifier
                            .background(br.chipBg, RoundedCornerShape(10.dp))
                            .clickable { onClose() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("关闭", color = br.chipText, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
