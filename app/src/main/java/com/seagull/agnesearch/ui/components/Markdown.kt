package com.seagull.agnesearch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seagull.agnesearch.ui.theme.BrandColors
import com.seagull.agnesearch.ui.theme.brand

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Para(val text: String) : MdBlock()
    data class Bullet(val text: String, val marker: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Code(val lang: String, val body: String) : MdBlock()
    data class Table(val rows: List<List<String>>) : MdBlock()
    object Rule : MdBlock()
}

/**
 * 自己写的轻量 Markdown 渲染器：标题 / 列表 / 引用 / 代码块 / 表格 / 粗体斜体行内代码。
 * 不引第三方库，流式半截文本也不会崩。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    baseSize: Int = 15,
) {
    val br = brand
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    SelectionContainer {
    Column(modifier = modifier.fillMaxWidth()) {
        for (b in blocks) {
            when (b) {
                is MdBlock.Heading -> {
                    Spacer(Modifier.height(if (b.level <= 1) 14.dp else 10.dp))
                    Text(
                        text = inline(b.text, br, bold = true),
                        color = br.textPrimary,
                        fontSize = when (b.level) {
                            1 -> (baseSize + 6).sp
                            2 -> (baseSize + 3).sp
                            else -> (baseSize + 1).sp
                        },
                        fontWeight = FontWeight.Bold,
                        lineHeight = (baseSize + 9).sp,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                is MdBlock.Para -> {
                    Text(
                        text = inline(b.text, br),
                        color = br.textPrimary,
                        fontSize = baseSize.sp,
                        lineHeight = (baseSize + 8).sp,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }

                is MdBlock.Bullet -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = b.marker,
                            color = br.accent,
                            fontSize = baseSize.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(if (b.marker.length > 2) 26.dp else 16.dp),
                        )
                        Text(
                            text = inline(b.text, br),
                            color = br.textPrimary,
                            fontSize = baseSize.sp,
                            lineHeight = (baseSize + 8).sp,
                        )
                    }
                }

                is MdBlock.Quote -> {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(if (b.text.length > 60) 52.dp else 26.dp)
                                .background(br.accent, RoundedCornerShape(2.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = inline(b.text, br),
                            color = br.textSecondary,
                            fontSize = baseSize.sp,
                            lineHeight = (baseSize + 8).sp,
                        )
                    }
                }

                is MdBlock.Code -> {
                    CodeBlock(lang = b.lang, code = b.body)
                }

                is MdBlock.Table -> {
                    if (b.rows.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(br.codeBg, RoundedCornerShape(12.dp))
                                .padding(vertical = 8.dp),
                        ) {
                            for ((ri, row) in b.rows.withIndex()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    for (cell in row) {
                                        Text(
                                            text = inline(cell, br, bold = ri == 0),
                                            color = if (ri == 0) br.textPrimary else br.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = if (ri == 0) FontWeight.SemiBold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                is MdBlock.Rule -> {
                    Spacer(Modifier.height(8.dp))
                    ThinDivider()
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
    }
}

private fun parseMarkdown(src: String): List<MdBlock> {
    val lines = src.replace("\r\n", "\n").split("\n")
    val out = ArrayList<MdBlock>()
    val para = StringBuilder()
    var i = 0

    fun flushPara() {
        if (para.isNotEmpty()) {
            out.add(MdBlock.Para(para.toString().trim()))
            para.setLength(0)
        }
    }

    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trimEnd()
        val t = line.trim()

        when {
            t.startsWith("```") -> {
                flushPara()
                val lang = t.removePrefix("```").trim()
                i++
                val body = StringBuilder()
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    body.append(lines[i]).append("\n")
                    i++
                }
                out.add(MdBlock.Code(lang, body.toString().trimEnd()))
            }

            t.isEmpty() -> flushPara()

            t == "---" || t == "***" || t == "___" -> {
                flushPara()
                out.add(MdBlock.Rule)
            }

            t.startsWith("#") -> {
                flushPara()
                var lv = 0
                while (lv < t.length && t[lv] == '#') lv++
                out.add(MdBlock.Heading(lv.coerceIn(1, 4), t.substring(lv).trim()))
            }

            t.startsWith("> ") || t == ">" -> {
                flushPara()
                out.add(MdBlock.Quote(t.removePrefix(">").trim()))
            }

            isBullet(t) -> {
                flushPara()
                val marker = if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")) "•" else t.substringBefore(' ')
                toBulletText(t)?.let { out.add(MdBlock.Bullet(it, marker)) }
            }

            t.startsWith("|") && t.endsWith("|") -> {
                flushPara()
                val rows = ArrayList<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val cells = lines[i].trim().trim('|').split("|").map { it.trim() }
                    val isSep = cells.isNotEmpty() && cells.all { c -> c.isNotEmpty() && c.all { ch -> ch == '-' || ch == ':' } }
                    if (!isSep) rows.add(cells)
                    i++
                }
                i--
                out.add(MdBlock.Table(rows))
            }

            else -> {
                if (para.isNotEmpty()) para.append(" ")
                para.append(t)
            }
        }
        i++
    }
    flushPara()
    return out
}

private fun isBullet(t: String): Boolean {
    if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")) return true
    val dot = t.indexOf(". ")
    if (dot in 1..3) {
        return t.substring(0, dot).all { it.isDigit() }
    }
    return false
}

private fun toBulletText(t: String): String? {
    if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")) return t.substring(2).trim()
    val dot = t.indexOf(". ")
    if (dot in 1..3) {
        val n = t.substring(0, dot)
        if (n.all { it.isDigit() }) return t.substring(dot + 2).trim()
    }
    return null
}

private fun inline(text: String, br: BrandColors, bold: Boolean = false): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val e = text.indexOf("**", i + 2)
                if (e > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, e))
                    }
                    i = e + 2
                    continue
                }
            }
            if (text.startsWith("__", i)) {
                val e = text.indexOf("__", i + 2)
                if (e > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, e))
                    }
                    i = e + 2
                    continue
                }
            }
            if (text[i] == '`') {
                val e = text.indexOf('`', i + 1)
                if (e > i) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = br.chipBg,
                            color = br.chipText,
                        )
                    ) {
                        append(text.substring(i + 1, e))
                    }
                    i = e + 1
                    continue
                }
            }
            if (text[i] == '[') {
                val close = text.indexOf(']', i)
                if (close > i && close + 1 < text.length && text[close + 1] == '(') {
                    val end = text.indexOf(')', close + 1)
                    if (end > close) {
                        withStyle(SpanStyle(color = br.accent)) {
                            append(text.substring(i + 1, close))
                        }
                        i = end + 1
                        continue
                    }
                }
            }
            if (text[i] == '*' && !text.startsWith("**", i)) {
                val e = text.indexOf('*', i + 1)
                if (e > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, e))
                    }
                    i = e + 1
                    continue
                }
            }
            if (bold) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text[i].toString())
                }
            } else {
                append(text[i].toString())
            }
            i++
        }
    }
}

/** 纯文本工具，给来源摘要做单行压缩用。 */
fun oneLine(s: String, max: Int = 160): String {
    val t = s.replace(Regex("\\s+"), " ").trim()
    return if (t.length <= max) t else t.substring(0, max) + "…"
}

/** 代码块：右上角单独复制按钮 + 横向滚动，不换行。 */
@Composable
private fun CodeBlock(lang: String, code: String) {
    val br = brand
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    val hScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(br.codeBg, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                lang.ifBlank { "code" },
                color = br.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .background(br.card, RoundedCornerShape(8.dp))
                    .clickable {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    if (copied) "已复制" else "复制代码",
                    color = if (copied) br.accent else br.chipText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .horizontalScroll(hScroll)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        ) {
            Text(
                text = code,
                color = br.textPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 19.sp,
            )
        }
    }
}
