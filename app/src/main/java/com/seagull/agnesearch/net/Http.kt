package com.seagull.agnesearch.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * 全局 OkHttp 实例。支持在设置里填代理（例如 Clash 的 http://127.0.0.1:7890）。
 */
object Http {

    val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    val HTML_MEDIA = "text/html; charset=utf-8".toMediaType()

    private val UAS = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    )

    fun randomUa(): String = UAS[(Math.random() * UAS.size).toInt().coerceIn(0, UAS.size - 1)]

    @Volatile
    private var proxySpec: String = ""

    @Volatile
    private var client: OkHttpClient = build("")

    fun client(): OkHttpClient = client

    /** 设置里改了代理就调这个，空串表示直连。 */
    fun applyProxy(spec: String) {
        val s = spec.trim()
        if (s == proxySpec) return
        proxySpec = s
        client = build(s)
    }

    private fun build(spec: String): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(300, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)

        if (spec.isNotEmpty()) {
            try {
                val url = if (spec.contains("://")) spec else "http://$spec"
                val u = java.net.URI(url)
                val port = if (u.port > 0) u.port else 8080
                val host = u.host
                if (!host.isNullOrBlank()) {
                    b.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
                }
            } catch (e: Exception) {
                // 代理地址写错就当直连，不要炸
            }
        }
        return b.build()
    }

    /** 带随机 UA 的 GET。 */
    fun get(url: String, referer: String? = null): Request {
        val b = Request.Builder()
            .url(url)
            .header("User-Agent", randomUa())
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        if (referer != null) b.header("Referer", referer)
        return b.build()
    }
}
