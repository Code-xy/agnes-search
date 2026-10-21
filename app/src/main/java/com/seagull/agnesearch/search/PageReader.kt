package com.seagull.agnesearch.search

import com.seagull.agnesearch.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream

/**
 * 打开网页并把正文抽出来给模型看。整个 App 的「深度」都靠它。
 */
object PageReader {

    private const val MAX_CHARS = 6000

    private val DROP = "script,style,noscript,iframe,svg,canvas,form,nav,header,footer,aside," +
        "button,select,input,textarea,figure[class*=ad],div[class*=advert],div[id*=advert]," +
        "div[class*=comment],div[id*=comment],div[class*=sidebar],div[class*=footer]"

    private val CANDIDATES = listOf(
        "article", "main", "[role=main]",
        "div[class*=article-content]", "div[class*=articleContent]", "div[class*=post-content]",
        "div[class*=entry-content]", "div[class*=content-body]", "div[class*=markdown-body]",
        "div[id*=content]", "div[class*=content]", "div[class*=article]", "div[class*=post]",
        "section",
    )

    data class Page(val title: String, val url: String, val text: String)

    suspend fun read(url: String): Page? = withContext(Dispatchers.IO) {
        try {
            val req = Http.get(url)
            Http.client().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val ct = resp.header("Content-Type") ?: ""
                if (!ct.contains("html") && !ct.contains("text") && ct.isNotEmpty()) {
                    // PDF / 二进制之类不读
                    if (!ct.contains("xml")) return@withContext null
                }
                val bytes = resp.body?.bytes() ?: return@withContext null
                if (bytes.size > 4 * 1024 * 1024) return@withContext null

                // charset 交给 Jsoup 自己嗅探 meta
                val doc = Jsoup.parse(ByteArrayInputStream(bytes), null, url)
                doc.select(DROP).remove()

                val title = doc.title().trim()
                var best: String? = null
                var bestLen = 0
                for (sel in CANDIDATES) {
                    for (el in doc.select(sel)) {
                        val t = el.text()
                        if (t.length > bestLen) {
                            bestLen = t.length
                            best = t
                        }
                    }
                    // 已经找到足够长的正文就不必再试更松的选择器
                    if (bestLen > 1200) break
                }
                var text = best ?: doc.body()?.text().orEmpty()
                text = text.replace('\u00A0', ' ')
                    .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
                    .replace(Regex("\\n{3,}"), "\n\n")
                    .trim()
                if (text.length > MAX_CHARS) text = text.substring(0, MAX_CHARS) + " …（已截断）"
                if (text.length < 60) return@withContext null
                Page(title.ifEmpty { url }, url, text)
            }
        } catch (e: Exception) {
            null
        }
    }
}
