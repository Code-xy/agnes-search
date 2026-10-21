package com.seagull.agnesearch.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.seagull.agnesearch.core.Attachment

/**
 * 任意文件 → Attachment。
 *
 *  文本/代码类 → 读出正文（UTF-8 / GBK 自适应，超 200KB 截断）
 *  图片类      → 转 Data URI，走多模态输入
 *  其它（apk/zip/pdf/音视频）→ 只留元信息并给出提示，接口本身不支持这些模态
 */
object FilePicker {

    const val MAX_TEXT_CHARS = 200_000
    private const val HEAD_BYTES = 400_000

    /** 当成文本处理的扩展名（代码、配置、标记语言…） */
    private val TEXT_EXT = setOf(
        "txt", "text", "md", "markdown", "log", "csv", "tsv", "ini", "conf", "cfg", "toml", "env", "gitignore", "properties",
        "json", "json5", "xml", "yml", "yaml", "html", "htm", "css", "scss", "less", "svg",
        "kt", "kts", "java", "smali", "gradle", "groovy", "py", "pyw", "ipynb", "js", "mjs", "cjs", "ts", "jsx", "tsx",
        "c", "h", "cpp", "cc", "cxx", "hpp", "hh", "cs", "go", "rs", "rb", "php", "swift", "m", "mm", "dart", "lua", "r",
        "pl", "pm", "sh", "bash", "zsh", "fish", "bat", "cmd", "ps1", "sql", "asm", "s", "v", "vhd", "diff", "patch",
        "makefile", "cmake", "dockerfile", "tf", "tfvars", "proto", "graphql", "gql", "har", "pem", "crt", "key",
    )

    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "avif")

    fun read(ctx: Context, uri: Uri): Attachment? {
        val name = queryName(ctx, uri)
        val size = querySize(ctx, uri)
        val mime = try {
            ctx.contentResolver.getType(uri).orEmpty()
        } catch (e: Exception) {
            ""
        }
        val ext = name.substringAfterLast('.', "").lowercase()

        val isImage = mime.startsWith("image/") || ext in IMAGE_EXT
        if (isImage) {
            val data = ImagePicker.toDataUri(ctx, uri, 1600, 88)
                ?: return Attachment(name = name, size = size, kind = "other", mime = mime, note = "图片读取失败")
            return Attachment(name = name, size = size, kind = "image", dataUri = data, mime = mime)
        }

        val isText = mime.startsWith("text/") ||
            mime.contains("json") || mime.contains("xml") || mime.contains("yaml") ||
            mime.contains("javascript") || mime.contains("x-sh") || mime.contains("x-python") ||
            ext in TEXT_EXT || name.lowercase() in TEXT_EXT

        if (!isText) {
            return Attachment(
                name = name,
                size = size,
                kind = "other",
                mime = mime,
                note = "这类文件接口不支持（只吃纯文本和图片），已跳过内容",
            )
        }

        val bytes = try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(HEAD_BYTES)
                var n = 0
                var read = input.read(buf)
                // 只读前 HEAD_BYTES，避免超大文件把内存打爆
                while (read > 0 && n < HEAD_BYTES) {
                    n += read
                    if (n >= HEAD_BYTES) break
                    read = input.read(buf, n, HEAD_BYTES - n)
                }
                buf.copyOf(n)
            }
        } catch (e: Exception) {
            null
        } ?: return Attachment(name = name, size = size, kind = "other", mime = mime, note = "读取失败")

        var text = decodeText(bytes)
        val truncated = text.length > MAX_TEXT_CHARS
        if (truncated) text = text.substring(0, MAX_TEXT_CHARS)

        return Attachment(
            id = "a" + System.currentTimeMillis().toString(36),
            name = name,
            size = size,
            kind = "text",
            text = text,
            mime = mime,
            note = if (truncated) "内容过长，已截断到 " + (MAX_TEXT_CHARS / 1000) + "K 字符" else "",
        )
    }

    /** UTF-8 优先，出现替换字符就按 GBK 再来一次（国内老文件常见）。 */
    private fun decodeText(bytes: ByteArray): String {
        return try {
            val s = String(bytes, Charsets.UTF_8)
            if (s.contains('\uFFFD')) {
                try {
                    String(bytes, java.nio.charset.Charset.forName("GBK"))
                } catch (e: Exception) {
                    s
                }
            } else {
                s
            }
        } catch (e: Exception) {
            String(bytes, Charsets.UTF_8)
        }
    }

    private fun queryName(ctx: Context, uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: "file"
        return try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) {
                        val n = c.getString(i)
                        if (!n.isNullOrBlank()) return n
                    }
                }
                null
            } ?: uri.lastPathSegment ?: "file"
        } catch (e: Exception) {
            uri.lastPathSegment ?: "file"
        }
    }

    private fun querySize(ctx: Context, uri: Uri): Long {
        if (uri.scheme == "file") return try {
            java.io.File(uri.path ?: "").length()
        } catch (e: Exception) {
            0L
        }
        return try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.SIZE)
                    if (i >= 0) return c.getLong(i)
                }
                0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> "未知大小"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.2f MB", bytes / 1024.0 / 1024.0)
    }

    /** 生成给模型看的附件代码块。 */
    fun asPromptBlock(a: Attachment): String {
        val ext = a.name.substringAfterLast('.', "").lowercase()
        val body = a.text.trimEnd()
        val head = "--- 附件：" + a.name + "（" + formatSize(a.size) + "）---"
        return head + "\n```" + ext + "\n" + body + "\n```"
    }
}
