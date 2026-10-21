package com.seagull.agnesearch.ui.search

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seagull.agnesearch.core.SourceItem
import com.seagull.agnesearch.ui.components.GlassCard
import com.seagull.agnesearch.ui.components.MarkdownText
import com.seagull.agnesearch.ui.components.Pill
import com.seagull.agnesearch.ui.components.SectionTitle
import com.seagull.agnesearch.ui.components.ThinDivider
import com.seagull.agnesearch.ui.components.oneLine
import com.seagull.agnesearch.ui.theme.brand

private val SUGGESTIONS = listOf(
    "2026 年安卓本地大模型推理框架哪个好用",
    "帮我查一下 Rizin 和 Ghidra 的反编译差异",
    "Agnes 2.5 Flash 和 3.0 Flash 有什么区别",
    "Python 3.14 有哪些新特性",
    "今天科技圈有什么值得关注的事",
)

@Composable
fun HomeScreen(
    vm: SearchViewModel,
    onOpenSettings: () -> Unit,
) {
    val br = brand
    val st = vm.state
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
    ) {
        item(key = "header") {
            Header(onOpenSettings)
        }

        item(key = "searchbox") {
            SearchBox(
                value = st.query,
                onValueChange = { vm.setQuery(it) },
                onSubmit = { vm.submit() },
                running = st.running,
                onStop = { vm.stop() },
                onClear = { vm.clear() },
            )
        }

        item(key = "status") {
            if (st.running || st.error.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                if (st.error.isNotEmpty()) {
                    ErrorCard(st.error) { vm.submit() }
                } else {
                    RunningCard(st.status, st.steps)
                }
            }
        }

        item(key = "answer") {
            if (st.answer.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                AnswerCard(
                    answer = st.answer,
                    model = st.model,
                    elapsedMs = st.elapsedMs,
                    tokens = st.promptTokens + st.completionTokens,
                    running = st.running,
                )
            }
        }

        item(key = "sources") {
            if (st.sources.isNotEmpty()) {
                SourcesSection(st.sources, st.done)
            }
        }

        item(key = "welcome") {
            if (st.answer.isEmpty() && !st.running && st.error.isEmpty()) {
                Welcome(onPick = { vm.useQuery(it) })
            }
        }
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    val br = brand
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(Brush.linearGradient(br.gradient), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Agnes 搜索", color = br.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "会自己查资料的 AI 搜索",
                color = br.textSecondary,
                fontSize = 11.sp,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "设置", tint = br.textSecondary)
        }
    }
}

@Composable
private fun SearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    running: Boolean,
    onStop: () -> Unit,
    onClear: () -> Unit,
) {
    val br = brand
    val focus = LocalFocusManager.current
    val shape = RoundedCornerShape(22.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(br.card, shape)
            .border(1.dp, br.cardBorder, shape)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (value.isEmpty()) {
                Text("问点什么，我去查", color = br.textSecondary, fontSize = 15.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = br.textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(br.accent),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    onSubmit()
                }),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (value.isNotEmpty() && !running) {
            IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "清空",
                    tint = br.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    if (running) Brush.linearGradient(listOf(br.danger, br.danger))
                    else Brush.linearGradient(br.gradient),
                    CircleShape,
                )
                .clickable {
                    if (running) onStop() else {
                        focus.clearFocus()
                        onSubmit()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (running) Icons.Filled.Close else Icons.Filled.ArrowForward,
                contentDescription = if (running) "停止" else "搜索",
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LoadingDots() {
    val br = brand
    val inf = rememberInfiniteTransition(label = "dots")
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..2) {
            val a by inf.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 620, delayMillis = i * 160),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(6.dp)
                    .background(br.accent.copy(alpha = a), CircleShape),
            )
        }
    }
}

@Composable
private fun RunningCard(status: String, steps: List<String>) {
    val br = brand
    GlassCard(corner = 18, padding = 14) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingDots()
                Spacer(Modifier.width(10.dp))
                Text(
                    status.ifEmpty { "正在思考…" },
                    color = br.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (steps.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ThinDivider()
                Spacer(Modifier.height(8.dp))
                for (s in steps.takeLast(6)) {
                    Text(
                        "· " + oneLine(s, 90),
                        color = br.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    val br = brand
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(br.card, shape)
            .border(1.dp, br.danger.copy(alpha = 0.5f), shape)
            .padding(14.dp),
    ) {
        Text("出错了", color = br.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(message, color = br.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.clickable { onRetry() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = br.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("重试", color = br.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AnswerCard(
    answer: String,
    model: String,
    elapsedMs: Long,
    tokens: Int,
    running: Boolean,
) {
    val br = brand
    GlassCard(corner = 20, padding = 16) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (running) br.accent else br.gradient[1], CircleShape),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    if (running) "生成中" else "回答",
                    color = br.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                val meta = buildString {
                    if (model.isNotEmpty()) append(model)
                    if (elapsedMs > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(String.format("%.1fs", elapsedMs / 1000.0))
                    }
                    if (tokens > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(tokens).append(" tok")
                    }
                }
                if (meta.isNotEmpty()) {
                    Text(meta, color = br.textSecondary, fontSize = 10.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.height(10.dp))
            MarkdownText(markdown = answer)
        }
    }
}

@Composable
private fun SourcesSection(sources: List<SourceItem>, done: Boolean) {
    val br = brand
    val uri = LocalUriHandler.current
    Column {
        SectionTitle(
            text = if (done) "来源" else "已检索到的来源",
            trailing = sources.size.toString() + " 条",
        )
        for ((i, s) in sources.withIndex()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .background(br.card, RoundedCornerShape(16.dp))
                    .border(1.dp, br.cardBorder, RoundedCornerShape(16.dp))
                    .clickable { uri.openUri(s.url) }
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(br.chipBg, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (i + 1).toString(),
                        color = br.chipText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        s.title.ifEmpty { s.url },
                        color = br.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (s.snippet.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            oneLine(s.snippet, 110),
                            color = br.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        host(s.url) + if (s.content.isNotBlank()) "  · 已读正文" else "",
                        color = br.accent,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun Welcome(onPick: (String) -> Unit) {
    val br = brand
    Column {
        Spacer(Modifier.height(8.dp))
        GlassCard(corner = 20, padding = 16) {
            Column {
                Text(
                    "它不是只会在对话框里编答案",
                    color = br.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                AbilityRow("1", "先联网检索", "按你的问题自己决定搜什么、搜几轮")
                AbilityRow("2", "再打开原文", "摘要不够就抓正文，关键信息有出处")
                AbilityRow("3", "最后带引用回答", "每句结论都标编号，点来源直接跳原文")
                Spacer(Modifier.height(6.dp))
                Text(
                    "接口兼容 OpenAI 格式，端点 / Key / 模型都能在设置里改。",
                    color = br.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }

        SectionTitle(text = "试试这些", trailing = "点一下直接搜")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in SUGGESTIONS) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(br.card, RoundedCornerShape(14.dp))
                        .border(1.dp, br.cardBorder, RoundedCornerShape(14.dp))
                        .clickable { onPick(s) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = br.accent,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        s,
                        color = br.textPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AbilityRow(num: String, title: String, desc: String) {
    val br = brand
    Row(modifier = Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(Brush.linearGradient(br.gradient), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(num, color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = br.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = br.textSecondary, fontSize = 11.sp)
        }
    }
}

fun host(url: String): String {
    return try {
        url.substringAfter("://").substringBefore("/").removePrefix("www.")
    } catch (e: Exception) {
        url
    }
}
