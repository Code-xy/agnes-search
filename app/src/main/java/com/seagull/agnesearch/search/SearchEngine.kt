package com.seagull.agnesearch.search

import com.seagull.agnesearch.core.AppSettings
import com.seagull.agnesearch.core.SourceItem
import com.seagull.agnesearch.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.net.URLEncoder
import android.util.Base64

/**
 * 自建搜索后端：不依赖任何第三方付费 key，直接抓公开搜索页。
 * 单引擎失败自动降级到下一个，保证 Agent 不至于因为一个源被墙就整条链挂掉。
 */
object SearchEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    sealed class Result {
        data class Ok(val items: List<SourceItem>, val engine: String) : Result()
        data class Fail(val message: String) : Result()
    }

    /** 按设置挑引擎，逐个重试。 */
    suspend fun search(query: String, cfg: AppSettings): Result = withContext(Dispatchers.IO) {
        val limit = cfg.maxResults.coerceIn(3, 20)
        val order: List<String> = when (cfg.searchProvider) {
            "baidu" -> listOf("baidu", "bing", "ddg")
            "bing" -> listOf("bing", "bing_intl", "ddg")
            "duckduckgo" -> listOf("ddg", "ddg_lite", "bing")
            "searxng" -> listOf("searxng", "bing", "ddg")
            else -> {
                val l = mutableListOf<String>()
                if (cfg.searxUrl.isNotBlank()) l.add("searxng")
                l.add("baidu")
                l.add("bing")
                l.add("bing_intl")
                l.add("ddg")
                l.add("ddg_lite")
                l
            }
        }

        val errors = mutableListOf<String>()
        for (engine in order) {
            val items = try {
                when (engine) {
                    "baidu" -> baidu(query, limit)
                    "bing" -> bing(query, limit, false)
                    "bing_intl" -> bing(query, limit, true)
                    "ddg" -> duck(query, limit, false)
                    "ddg_lite" -> duck(query, limit, true)
                    "searxng" -> searx(query, limit, cfg.searxUrl)
                    else -> emptyList()
                }
            } catch (e: Exception) {
                errors.add(engine + ":" + (e.message ?: e.javaClass.simpleName))
                emptyList()
            }
            if (items.isNotEmpty()) {
                return@withContext Result.Ok(items, engineLabel(engine))
            } else if (errors.none { it.startsWith(engine + ":") }) {
                errors.add(engine + ": 无结果")
            }
        }
        Result.Fail("所有搜索源都失败了（" + errors.joinToString("; ").take(300) + "）")
    }

    private fun engineLabel(e: String) = when (e) {
        "baidu" -> "百度"
        "bing" -> "Bing 中国"
        "bing_intl" -> "Bing"
        "ddg" -> "DuckDuckGo"
        "ddg_lite" -> "DuckDuckGo Lite"
        "searxng" -> "SearXNG"
        else -> e
    }

    // ── 百度 ────────────────────────────────────────────────────────────

    /**
     * 百度对国内直连最稳，中文结果也好。
     * 结果链接多为 baidu.com/link?url=… 跳转链，浏览器和 open_url 都能自动跟随。
     */
    private suspend fun baidu(query: String, limit: Int): List<SourceItem> {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.baidu.com/s?wd=" + q + "&rn=20&ie=utf-8&tn=baiduhome_pg"
        val html = fetchText(url, "https://www.baidu.com/", false) ?: return emptyList()
        val doc = Jsoup.parse(html, "https://www.baidu.com/")

        val out = mutableListOf<SourceItem>()
        for (el in doc.select("div.result, div.c-container")) {
            val a = el.selectFirst("h3 a") ?: continue
            val href = a.attr("href").trim()
            val rawTitle = a.text().trim()
            if (rawTitle.isEmpty() || href.isEmpty()) continue
            if (!href.startsWith("http")) continue
            var sn = el.selectFirst(".c-abstract")?.text()?.trim().orEmpty()
            if (sn.isEmpty()) sn = el.selectFirst("[class*=content-right]")?.text()?.trim().orEmpty()
            if (sn.isEmpty()) {
                // 结构变了就退化成"块内文本减标题"
                sn = el.text().replace(rawTitle, "").trim().take(220)
            }
            out.add(SourceItem(rawTitle, href, sn, "百度"))
            if (out.size >= limit) break
        }
        return resolveBaiduUrls(out, 6)
    }

    /**
     * 百度给的是 baidu.com/link?url=… 跳转链。
     * 并发地把前几条换成真实地址，失败就保留原链（浏览器照样能跳）。
     */
    private suspend fun resolveBaiduUrls(items: List<SourceItem>, max: Int): List<SourceItem> =
        coroutineScope {
            items.mapIndexed { idx, it ->
                async {
                    if (idx >= max || !it.url.contains("baidu.com/link")) it
                    else {
                        val real = withTimeoutOrNull(6000) { followRedirect(it.url) }
                        if (real.isNullOrBlank()) it else it.copy(url = real)
                    }
                }
            }.awaitAll()
        }

    private suspend fun followRedirect(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", Http.randomUa())
                .header("Accept", "text/html,*/*")
                .get()
                .build()
            Http.client().newCall(req).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                if (finalUrl.contains("baidu.com/link")) null else finalUrl
            }
        } catch (e: Exception) {
            null
        }
    }

    // ── Bing ────────────────────────────────────────────────────────────

    private fun bing(query: String, limit: Int, intl: Boolean): List<SourceItem> {
        val host = if (intl) "https://www.bing.com" else "https://cn.bing.com"
        val q = URLEncoder.encode(query, "UTF-8")
        val url = host + "/search?q=" + q + "&count=30&setlang=zh-CN&mkt=zh-CN&ensearch=" + (if (intl) "1" else "0")
        val html = fetchText(url, "https://cn.bing.com/", false) ?: return emptyList()
        val doc = Jsoup.parse(html, host)

        val out = mutableListOf<SourceItem>()
        val nodes = doc.select("li.b_algo")
        for (n in nodes) {
            val a = n.selectFirst("h2 a") ?: continue
            val title = a.text().trim()
            val href = cleanBing(a.attr("href"))
            if (title.isEmpty() || href.isEmpty()) continue
            val snippet = n.selectFirst(".b_caption p")?.text()
                ?: n.selectFirst("p")?.text()
                ?: n.selectFirst(".b_lineclamp2")?.text()
                ?: ""
            out.add(SourceItem(title = title, url = href, snippet = snippet.trim(), engine = engineLabel(if (intl) "bing_intl" else "bing")))
            if (out.size >= limit) break
        }
        return out
    }

    /** Bing 结果外链被包了一层 /ck/a?..., u=a1<base64url> */
    private fun cleanBing(href: String): String {
        if (!href.contains("/ck/a")) return href
        return try {
            val q = href.substringAfter("?", "")
            var u: String? = null
            for (kv in q.split("&")) {
                if (kv.startsWith("u=")) {
                    u = URLDecoder.decode(kv.substring(2), "UTF-8")
                    break
                }
            }
            var raw = u ?: return href
            if (raw.startsWith("a1")) raw = raw.substring(2)
            raw = raw.replace('-', '+').replace('_', '/')
            while (raw.length % 4 != 0) raw += "="
            val decoded = String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8)
            if (decoded.startsWith("http")) decoded else href
        } catch (e: Exception) {
            href
        }
    }

    // ── DuckDuckGo ──────────────────────────────────────────────────────

    private fun duck(query: String, limit: Int, lite: Boolean): List<SourceItem> {
        return if (lite) duckLite(query, limit) else duckHtml(query, limit)
    }

    private fun duckHtml(query: String, limit: Int): List<SourceItem> {
        val form = "q=" + URLEncoder.encode(query, "UTF-8") + "&kl=cn-zh"
        val html = postForm("https://html.duckduckgo.com/html/", form, "https://html.duckduckgo.com/")
            ?: return emptyList()
        val doc = Jsoup.parse(html, "https://html.duckduckgo.com/")
        val out = mutableListOf<SourceItem>()
        for (n in doc.select("div.result, div.web-result")) {
            val a = n.selectFirst("a.result__a") ?: continue
            val url = unwrapDuck(a.attr("href"))
            val title = a.text().trim()
            if (title.isEmpty() || url.isEmpty()) continue
            val sn = n.selectFirst("a.result__snippet")?.text()
                ?: n.selectFirst(".result__snippet")?.text() ?: ""
            out.add(SourceItem(title, url, sn.trim(), "DuckDuckGo"))
            if (out.size >= limit) break
        }
        return out
    }

    private fun duckLite(query: String, limit: Int): List<SourceItem> {
        val q = URLEncoder.encode(query, "UTF-8")
        val html = fetchText("https://lite.duckduckgo.com/lite/?q=" + q, "https://lite.duckduckgo.com/", false)
            ?: return emptyList()
        val doc = Jsoup.parse(html, "https://lite.duckduckgo.com/")
        val out = mutableListOf<SourceItem>()
        for (a in doc.select("a.result-link")) {
            val url = unwrapDuck(a.attr("href"))
            val title = a.text().trim()
            if (title.isEmpty() || url.isEmpty()) continue
            val sn = a.parent()?.nextElementSibling()?.text()?.trim() ?: ""
            out.add(SourceItem(title, url, sn, "DuckDuckGo Lite"))
            if (out.size >= limit) break
        }
        return out
    }

    private fun unwrapDuck(href: String): String {
        if (href.startsWith("//")) {
            val full = "https:$href"
            val uddg = try {
                Regex("[?&]uddg=([^&]+)").find(full)?.groupValues?.get(1)
            } catch (e: Exception) {
                null
            }
            return if (uddg != null) URLDecoder.decode(uddg, "UTF-8") else full
        }
        return href
    }

    // ── SearXNG ─────────────────────────────────────────────────────────

    private fun searx(query: String, limit: Int, base: String): List<SourceItem> {
        if (base.isBlank()) return emptyList()
        var b = base.trim().trimEnd('/')
        if (!b.startsWith("http")) b = "https://$b"
        val q = URLEncoder.encode(query, "UTF-8")
        val html = fetchText(b + "/search?q=" + q + "&format=json&language=zh-CN", b + "/", true)
        if (html.isNullOrBlank()) return emptyList()

        // 优先解析 JSON，其次退化到 HTML
        try {
            val arr = json.parseToJsonElement(html).jsonObject["results"] as? JsonArray
            if (arr != null) {
                val out = mutableListOf<SourceItem>()
                for (el in arr) {
                    val o = el as? JsonObject ?: continue
                    val url = o["url"]?.jsonPrimitive?.contentOrNull ?: continue
                    val title = o["title"]?.jsonPrimitive?.contentOrNull ?: url
                    val sn = o["content"]?.jsonPrimitive?.contentOrNull ?: ""
                    out.add(SourceItem(title, url, sn, "SearXNG"))
                    if (out.size >= limit) break
                }
                if (out.isNotEmpty()) return out
            }
        } catch (e: Exception) {
            // 不是 JSON，走 HTML 解析
        }

        val doc = Jsoup.parse(html, b)
        val out = mutableListOf<SourceItem>()
        for (n in doc.select("article.result, div.result")) {
            val a = n.selectFirst("h3 a") ?: n.selectFirst("a.url") ?: continue
            val url = a.attr("href")
            val title = a.text().trim()
            if (title.isEmpty() || !url.startsWith("http")) continue
            val sn = n.selectFirst("p.content")?.text() ?: ""
            out.add(SourceItem(title, url, sn.trim(), "SearXNG"))
            if (out.size >= limit) break
        }
        return out
    }

    // ── HTTP ────────────────────────────────────────────────────────────

    private fun fetchText(url: String, referer: String?, jsonAccept: Boolean): String? {
        return try {
            val b = Request.Builder()
                .url(url)
                .header("User-Agent", Http.randomUa())
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Accept", if (jsonAccept) "application/json,text/plain,*/*" else "text/html,application/xhtml+xml,*/*;q=0.8")
            if (referer != null) b.header("Referer", referer)
            Http.client().newCall(b.get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                decodeBody(resp.body?.bytes(), resp.header("Content-Type"))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun postForm(url: String, form: String, referer: String?): String? {
        return try {
            val b = Request.Builder()
                .url(url)
                .header("User-Agent", Http.randomUa())
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", referer ?: "https://duckduckgo.com")
            if (referer != null) b.header("Referer", referer)
            Http.client().newCall(b.post(form.toRequestBody(Http.HTML_MEDIA)).build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                decodeBody(resp.body?.bytes(), resp.header("Content-Type"))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeBody(bytes: ByteArray?, contentType: String?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        val lower = (contentType ?: "").lowercase()
        val cs = try {
            when {
                lower.contains("charset=") -> java.nio.charset.Charset.forName(
                    lower.substringAfter("charset=").trim().trim('"').substringBefore(';').trim()
                )
                lower.contains("gbk") || lower.contains("gb2312") -> java.nio.charset.Charset.forName("GBK")
                else -> Charsets.UTF_8
            }
        } catch (e: Exception) {
            Charsets.UTF_8
        }
        return String(bytes, cs)
    }

    fun parseDoc(html: String, base: String): Document = Jsoup.parse(html, base)
}
