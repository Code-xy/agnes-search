package com.seagull.agnesearch.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seagull.agnesearch.net.Http
import com.seagull.agnesearch.ui.theme.brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * 极简远程 / 本地图片加载（自己实现，不引图片库）。
 * 支持 http(s) URL 和本地文件绝对路径，带内存缓存。
 */
private val memCache = object : LruCache<String, ImageBitmap>(24) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = 1
}

private val failed = mutableSetOf<String>()

@Composable
fun RemoteImage(
    src: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val br = brand
    var bmp by remember(src) { mutableStateOf(memCache.get(src)) }

    LaunchedEffect(src) {
        if (src.isBlank() || bmp != null) return@LaunchedEffect
        if (failed.contains(src)) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { loadImage(src) }
        if (loaded != null) {
            memCache.put(src, loaded)
            bmp = loaded
        } else {
            failed.add(src)
        }
    }

    Box(
        modifier = modifier.background(br.codeBg),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            LoadingDotsSmall()
        }
    }
}

@Composable
private fun LoadingDotsSmall() {
    val br = brand
    Text("载入中…", color = br.textSecondary, fontSize = 11.sp)
}

private fun loadImage(src: String): ImageBitmap? {
    return try {
        val bytes: ByteArray? = if (src.startsWith("http")) {
            val req = Request.Builder().url(src).header("User-Agent", Http.randomUa()).get().build()
            Http.client().newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        } else {
            val f = File(src)
            if (f.exists()) f.readBytes() else null
        }
        if (bytes == null || bytes.isEmpty()) return null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

/** 缩略图列表用的方图。 */
@Composable
fun Thumb(src: String, size: Int, modifier: Modifier = Modifier) {
    RemoteImage(src = src, modifier = modifier.size(size.dp))
}
