package com.seagull.agnesearch.ui.history

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seagull.agnesearch.core.Chat
import com.seagull.agnesearch.core.Creation
import com.seagull.agnesearch.core.Store
import com.seagull.agnesearch.ui.components.ConfirmDialog
import com.seagull.agnesearch.ui.components.EmptyState
import com.seagull.agnesearch.ui.components.Pill
import com.seagull.agnesearch.ui.components.RemoteImage
import com.seagull.agnesearch.ui.components.TextInput
import com.seagull.agnesearch.ui.components.oneLine
import com.seagull.agnesearch.ui.create.ImageViewerDialog
import com.seagull.agnesearch.ui.theme.brand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(onOpenChat: (String) -> Unit) {
    val br = brand
    val chats by Store.chats.collectAsState()
    val creations by Store.creations.collectAsState()
    var keyword by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("chat") }
    var viewing by remember { mutableStateOf<Creation?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val myChats = chats.filter { c ->
        keyword.isBlank() || c.title.contains(keyword, true) ||
            c.messages.any { it.content.contains(keyword, true) }
    }
    val myCreations = creations.filter { c ->
        c.kind == tab && (keyword.isBlank() || c.prompt.contains(keyword, true))
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))
        Text("历史记录", color = br.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "本地保存 · 会话 ${chats.size} · 图像 ${creations.count { it.kind == "image" }} · 视频 ${creations.count { it.kind == "video" }}",
            color = br.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )

        TextInput(
            value = keyword,
            onValueChange = { keyword = it },
            placeholder = "在自己的记录里搜",
        )

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("对话", tab == "chat") { tab = "chat" }
            Pill("图像", tab == "image") { tab = "image" }
            Pill("视频", tab == "video") { tab = "video" }
            Spacer(Modifier.weight(1f))
            val canClear = if (tab == "chat") chats.isNotEmpty() else creations.any { it.kind == tab }
            if (canClear) {
                Pill("清空") { confirmClear = true }
            }
        }

        if (tab == "chat") {
            if (myChats.isEmpty()) {
                EmptyState(
                    title = if (chats.isEmpty()) "还没有会话" else "没有匹配的会话",
                    desc = if (chats.isEmpty()) "去对话页开一个新会话" else "换个关键词看看",
                    icon = Icons.Filled.MailOutline,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(myChats, key = { it.id }) { c ->
                        ChatCard(
                            c = c,
                            onOpen = { onOpenChat(c.id) },
                            onStar = { Store.toggleChatStar(c.id) },
                            onDelete = { Store.deleteChat(c.id) },
                        )
                    }
                }
            }
        } else {
            if (myCreations.isEmpty()) {
                EmptyState(
                    title = "还没有" + (if (tab == "image") "图像" else "视频") + "记录",
                    desc = "去创作页生成一个试试",
                    icon = Icons.Filled.MailOutline,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(myCreations, key = { it.id }) { c ->
                        CreationCard(c = c, onOpen = { viewing = c })
                    }
                }
            }
        }
    }

    val v = viewing
    if (v != null) {
        ImageViewerDialog(c = v, onClose = { viewing = null })
    }

    if (confirmClear) {
        val isChat = tab == "chat"
        val count = if (isChat) chats.size else creations.count { it.kind == tab }
        ConfirmDialog(
            title = if (isChat) "清空所有会话" else "清空所有" + (if (tab == "image") "图像" else "视频") + "记录",
            message = "将删除 " + count + " 条记录，删掉就找不回来了。",
            confirmText = "全部删除",
            onConfirm = {
                if (isChat) Store.clearChats() else Store.clearCreations()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun ChatCard(
    c: Chat,
    onOpen: () -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit,
) {
    val br = brand
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(br.card, shape)
            .border(1.dp, if (c.starred) br.accent.copy(alpha = 0.5f) else br.cardBorder, shape)
            .clickable { onOpen() }
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.MailOutline,
                contentDescription = null,
                tint = br.accent,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                c.title,
                color = br.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onStar, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "收藏",
                    tint = if (c.starred) br.accent else br.cardBorder,
                    modifier = Modifier.size(17.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = br.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        val last = c.messages.lastOrNull()
        if (last != null) {
            Spacer(Modifier.height(5.dp))
            Text(
                oneLine(last.content, 110),
                color = br.textSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmt(c.updatedAt), color = br.textSecondary, fontSize = 10.sp)
            Text("  ·  ${c.messages.size} 条消息", color = br.textSecondary, fontSize = 10.sp)
            if (c.model.isNotEmpty()) {
                Text("  ·  ${c.model}", color = br.textSecondary, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CreationCard(c: Creation, onOpen: () -> Unit) {
    val br = brand
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(br.card, shape)
            .border(1.dp, br.cardBorder, shape)
            .clickable { onOpen() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            src = if (c.localPath.isNotBlank()) c.localPath else c.url,
            modifier = Modifier.size(62.dp).background(br.codeBg, RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                c.prompt.ifBlank { "（无提示词）" },
                color = br.textPrimary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    append(c.model)
                    if (c.size.isNotBlank()) append(" · ").append(c.size)
                    if (c.seconds.isNotBlank()) append(" · ").append(c.seconds).append("s")
                },
                color = br.textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                statusLabel(c) + "   " + fmt(c.createdAt),
                color = if (c.status == "failed") br.danger else br.accent,
                fontSize = 10.sp,
            )
        }
        IconButton(onClick = { Store.deleteCreation(c.id) }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除",
                tint = br.textSecondary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

private fun statusLabel(c: Creation): String = when (c.status) {
    "done", "completed" -> "已完成"
    "failed" -> "失败" + (if (c.error.isNotBlank()) "：" + c.error.take(40) else "")
    else -> c.status + " " + c.progress + "%"
}

private fun fmt(ts: Long): String {
    if (ts <= 0) return ""
    return try {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    } catch (e: Exception) {
        ""
    }
}
