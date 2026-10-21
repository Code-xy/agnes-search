package com.seagull.agnesearch.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 相册 / 相机图片 → Data URI。
 *
 * 关键：**两段式解码**。先只读边界拿到原始尺寸，算出 inSampleSize 再真正解码，
 * 避免 4000x3000 原图直接进内存（那会让 App 明显卡顿甚至 OOM）。
 */
object ImagePicker {

    fun toDataUri(ctx: Context, uri: Uri, maxDim: Int = 1280, quality: Int = 85): String? {
        return try {
            val resolver = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, Rect(), bounds)
            }
            val sample = calcSample(bounds.outWidth, bounds.outHeight, maxDim)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, opts)
            } ?: return null
            compressToDataUri(bmp, quality)
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /** 相机返回的 Bitmap（可能很大）转 Data URI。 */
    fun bitmapToDataUri(bitmap: Bitmap, maxDim: Int = 1280, quality: Int = 88): String? {
        return try {
            compressToDataUri(bitmap, quality, maxDim)
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun compressToDataUri(src: Bitmap, quality: Int, maxDim: Int = 0): String? {
        val out = if (maxDim > 0) scale(src, maxDim) else src
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64," + b64
    }

    /** 原图长边 / 目标长边，向上取 2 的幂。 */
    private fun calcSample(width: Int, height: Int, maxDim: Int): Int {
        if (width <= 0 || height <= 0) return 1
        val longSide = if (width > height) width else height
        var sample = 1
        while (longSide / (sample * 2) >= maxDim) sample *= 2
        return sample.coerceAtLeast(1)
    }

    private fun scale(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longest = if (w > h) w else h
        if (longest <= maxDim) return src
        val ratio = maxDim.toFloat() / longest
        val nw = (w * ratio).toInt().coerceAtLeast(1)
        val nh = (h * ratio).toInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(src, nw, nh, true)
        } catch (e: Exception) {
            src
        }
    }

    /** 兼容旧调用。 */
    fun toDataUriSimple(ctx: Context, uri: Uri, maxDim: Int = 1280, quality: Int = 85): String? {
        return toDataUri(ctx, uri, maxDim, quality)
    }
}
