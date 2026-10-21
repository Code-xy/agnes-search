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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seagull.agnesearch.BuildConfig
import com.seagull.agnesearch.core.Store
import com.seagull.agnesearch.ui.components.ConfirmDialog
import com.seagull.agnesearch.ui.components.GlassCard
import com.seagull.agnesearch.ui.components.LetterAvatar
import com.seagull.agnesearch.ui.components.SectionTitle
import com.seagull.agnesearch.ui.components.ThinDivider
import com.seagull.agnesearch.ui.theme.brand

@Composable
fun ProfileScreen(onOpenSettings: () -> Unit) {
    val br = brand
    val usage by Store.usage.collectAsState()
    val settings by Store.settings.collectAsState()
    val chats by Store.chats.collectAsState()
    val creations by Store.creations.collectAsState()
    var showAbout by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<PendingConfirm?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
    ) {
        item {
            GlassCard(corner = 22, padding = 16) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LetterAvatar("A", 54)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Agnes 用户", color = br.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            settings.model,
                            color = br.chipText,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .background(br.chipBg, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (settings.apiKey.isBlank()) "未配置 API Key" else "Key " + mask(settings.apiKey),
                            color = if (settings.apiKey.isBlank()) br.danger else br.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "接口端点",
                        color = br.textSecondary,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        settings.baseUrl,
                        color = br.textPrimary,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                }
            }
        }

        item {
            SectionTitle("使用统计", "仅本机")
            GlassCard(corner = 20, padding = 16) {
                Column {
                    Row {
                        StatCell("对话消息", usage.messages.toString(), Modifier.weight(1f))
                        StatCell("生成图像", usage.images.toString(), Modifier.weight(1f))
                        StatCell("生成视频", usage.videos.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row {
                        StatCell("模型调用", usage.calls.toString(), Modifier.weight(1f))
                        StatCell("会话数", chats.size.toString(), Modifier.weight(1f))
                        StatCell("创作记录", creations.size.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    ThinDivider()
                    Spacer(Modifier.height(14.dp))
                    Row {
                        StatCell("输入 Token", compact(usage.promptTokens), Modifier.weight(1f))
                        StatCell("输出 Token", compact(usage.completionTokens), Modifier.weight(1f))
                        StatCell("估算花费", "$0.00", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Agnes 文本模型当前为活动价 \$0 / 百万 Token，这里按官方刊例价估算。",
                        color = br.textSecondary,
                        fontSize = 10.sp,
                    )
                }
            }
        }

        item {
            SectionTitle("设置")
            GlassCard(corner = 20, padding = 4) {
                Column {
                    MenuRow(Icons.Filled.Settings, "接口设置", "端点 / Key / 模型 / 参数", onOpenSettings)
                    ThinDivider()
                    MenuRow(Icons.Filled.Refresh, "清空用量统计", "只清计数，不动会话与创作") {
                        confirm = PendingConfirm(
                            "清空用量统计",
                            "只会把「对话消息 / 生成图像 / 生成视频 / Token」这些计数清零，会话和创作记录都保留。",
            "确认清空",
                        ) { Store.resetUsage() }
                    }
                    ThinDivider()
                    MenuRow(Icons.Filled.Delete, "清空全部记录", chats.size.toString() + " 会话 · " + creations.size + " 创作") {
                        confirm = PendingConfirm(
                            "清空全部记录",
                            "将删除 " + chats.size + " 个会话和 " + creations.size + " 条创作记录，删掉就找不回来了。",
            "全部删除",
                        ) {
                            Store.clearChats()
                            Store.clearCreations()
                        }
                    }
                    ThinDivider()
                    MenuRow(Icons.Filled.Info, "关于", "v" + BuildConfig.VERSION_NAME) {
                        showAbout = true
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(br.chipBg, br.card)),
                        RoundedCornerShape(18.dp),
                    )
                    .border(1.dp, br.cardBorder, RoundedCornerShape(18.dp))
                    .padding(14.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = br.accent,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text("数据在哪", color = br.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Key、历史、用量全部只存在这台手机上，不上传任何服务器。请求直接从本机发往你自己配置的端点。",
                        color = br.textSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }

    val pending = confirm
    if (pending != null) {
        ConfirmDialog(
            title = pending.title,
            message = pending.message,
            confirmText = pending.confirmText,
            onConfirm = {
                pending.action()
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("Agnes 客户端") },
            text = {
                Text(
                    "版本 v" + BuildConfig.VERSION_NAME + "\n\n" +
                        "接入 Agnes AI（OpenAI 兼容）的对话 / 图像 / 视频客户端，端点与 Key 全部可自配。\n" +
                        "默认接入 Agnes AI（OpenAI 兼容），端点与 Key 均可在设置里改成任意兼容服务。\n\n" +
                        "所有数据只存在本机；请求由本机直连你配置的端点。",
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    val br = brand
    Column(modifier = modifier) {
        Text(value, color = br.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = br.textSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    val br = brand
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = br.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = br.textPrimary, fontSize = 14.sp)
            if (desc.isNotEmpty()) Text(desc, color = br.textSecondary, fontSize = 11.sp)
        }
        Text("›", color = br.textSecondary, fontSize = 18.sp)
    }
}

private fun mask(key: String): String {
    if (key.length <= 10) return "••••••"
    return key.substring(0, 6) + "••••••" + key.substring(key.length - 4)
}

private fun compact(n: Long): String {
    return when {
        n >= 1_000_000 -> String.format("%.2fM", n / 1_000_000.0)
        n >= 1000 -> String.format("%.1fk", n / 1000.0)
        else -> n.toString()
    }
}

/** 待确认的危险操作。 */
private data class PendingConfirm(
    val title: String,
    val message: String,
    val confirmText: String,
    val action: () -> Unit,
)
