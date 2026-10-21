package com.seagull.agnesearch.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seagull.agnesearch.net.Http
import com.seagull.agnesearch.ui.theme.brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * 自研图片加载（不引图片库）。三种来源都吃：
 *   http(s) URL · data:image/...;base64  · 本地文件路径
 * 带内存缓存、按比例降采样、失败可点重试 —— 不会卡在"载入中"。
 */
private val memCache = object : LruCache<String, ImageBitmap>(32) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = 1
}

private sealed class ImgState {
    object Loading : ImgState()
    class Ok(val bmp: ImageBitmap) : ImgState()
    class Failed(val why: String) : ImgState()
}

@Composable
fun RemoteImage(
    src: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val br = brand
    var state by remember(src) { mutableStateOf<ImgState>(ImgState.Loading) }
    var attempt by remember(src) { mutableStateOf(0) }

    LaunchedEffect(src, attempt) {
        if (src.isBlank()) {
            state = ImgState.Failed("没有地址")
            return@LaunchedEffect
        }
        memCache.get(src)?.let {
            state = ImgState.Ok(it)
            return@LaunchedEffect
        }
        state = ImgState.Loading
        val loaded = withContext(Dispatchers.IO) { loadImage(src) }
        state = if (loaded != null) {
            memCache.put(src, loaded)
            ImgState.Ok(loaded)
        } else {
            ImgState.Failed(if (src.startsWith("http")) "下载失败" else "解码失败")
        }
    }

    Box(
        modifier = modifier.background(br.codeBg),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            is ImgState.Ok -> Image(
                bitmap = s.bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )

            is ImgState.Loading -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LoadingDots()
                Spacer(Modifier.height(6.dp))
                Text("载入中…", color = br.textSecondary, fontSize = 10.sp)
            }

            is ImgState.Failed -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { attempt += 1 },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    s.why,
                    color = br.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .background(br.chipBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text("点这里重试", color = br.chipText, fontSize = 10.sp)
                }
            }
        }
    }
}

private fun loadImage(src: String): ImageBitmap? {
    return try {
        val bytes: ByteArray? = when {
            src.startsWith("data:") -> decodeDataUri(src)
            src.startsWith("http") -> httpGet(src)
            src.startsWith("content://") || src.startsWith("file://") -> {
                val f = File(src.substringAfter("://"))
                if (f.exists()) f.readBytes() else null
            }
            else -> {
                val f = File(src)
                if (f.exists()) f.readBytes() else null
            }
        }
        if (bytes == null || bytes.isEmpty()) null else decodeSampled(bytes)
    } catch (e: Exception) {
        null
    }
}

private fun httpGet(url: String): ByteArray? {
    val req = Request.Builder().url(url).header("User-Agent", Http.randomUa()).get().build()
    return Http.client().newCall(req).execute().use { resp ->
        if (resp.isSuccessful) resp.body?.bytes() else null
    }
}

private fun decodeDataUri(src: String): ByteArray? {
    val comma = src.indexOf(',')
    if (comma < 0) return null
    val head = src.substring(0, comma)
    val payload = src.substring(comma + 1).trim()
    return try {
        if (head.contains("base64", true)) {
            Base64.decode(payload, Base64.DEFAULT)
        } else {
            java.net.URLDecoder.decode(payload, "UTF-8").toByteArray()
        }
    } catch (e: Exception) {
        null
    }
}

/** 大图先探尺寸再按需降采样，避免 4K 生成图把内存打爆。 */
private fun decodeSampled(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val longSide = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    if (longSide > 0) {
        while (longSide / sample > 1600) sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
}
