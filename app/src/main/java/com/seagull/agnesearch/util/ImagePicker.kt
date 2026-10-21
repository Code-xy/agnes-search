package com.seagull.agnesearch.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 相册图片 → Data URI（图像接口支持 data:image/...;base64,xxx 直接送）。
 * 先缩到长边 maxDim、JPEG 质量 85，控制请求体积。
 */
object ImagePicker {

    fun toDataUri(ctx: Context, uri: Uri, maxDim: Int = 1280, quality: Int = 85): String? {
        return try {
            val input: InputStream = ctx.contentResolver.openInputStream(uri) ?: return null
            val raw = input.use { BitmapFactory.decodeStream(it) } ?: return null
            val scaled = scale(raw, maxDim)
            val bos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64," + b64
        } catch (e: Exception) {
            null
        }
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
}
