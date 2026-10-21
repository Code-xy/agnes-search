package com.seagull.agnesearch.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把生成的图 / 视频存到系统相册。
 * API 29+ 走 MediaStore（免权限），更低版本写公共目录（需要存储权限）。
 */
object MediaSaver {

    fun saveImage(ctx: Context, bytes: ByteArray): String? {
        val name = "Agnes_" + stamp() + ".jpg"
        val bmp = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
        val out = try {
            java.io.ByteArrayOutputStream().use { bos ->
                if (bmp != null) bmp.compress(Bitmap.CompressFormat.JPEG, 95, bos)
                else bos.write(bytes)
                bos.toByteArray()
            }
        } catch (e: Exception) {
            bytes
        }

        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AgnesSearch")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = ctx.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { it.write(out) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Pictures/AgnesSearch/$name"
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "AgnesSearch"
                )
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, name)
                f.writeBytes(out)
                f.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    fun saveVideo(ctx: Context, bytes: ByteArray): String? {
        val name = "Agnes_" + stamp() + ".mp4"
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AgnesSearch")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = ctx.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Movies/AgnesSearch/$name"
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "AgnesSearch"
                )
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, name)
                f.writeBytes(bytes)
                f.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 生成内容落到 App 私有目录，URL 过期也能看。 */
    fun saveLocal(ctx: Context, id: String, ext: String, bytes: ByteArray): String? {
        return try {
            val dir = File(ctx.filesDir, "creations")
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, id + "." + ext)
            f.writeBytes(bytes)
            f.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}
