package com.seagull.agnesearch.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.seagull.agnesearch.core.Attachment
import com.seagull.agnesearch.ui.theme.brand
import com.seagull.agnesearch.util.FilePicker
import com.seagull.agnesearch.util.ImagePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 统一的内容来源：拍照 / 相册 / 任意文件（代码、文本、配置…）。
 * 拍照走 FileProvider 写 cacheDir，不申请 CAMERA 权限。
 * 图片 → Data URI 回调；文件 → Attachment 回调（文本类会把正文读出来）。
 */
class MediaPicker(
    val pickGallery: () -> Unit,
    val takePhoto: () -> Unit,
    val pickFile: () -> Unit,
)

@Composable
fun rememberMediaPicker(
    maxDim: Int = 1280,
    quality: Int = 85,
    onImage: (String) -> Unit,
    onFile: (Attachment) -> Unit,
    onNotice: (String) -> Unit = {},
): MediaPicker {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestImage by rememberUpdatedState(onImage)
    val latestFile by rememberUpdatedState(onFile)
    val latestNotice by rememberUpdatedState(onNotice)
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val gallery = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val d = withContext(Dispatchers.IO) { ImagePicker.toDataUri(ctx, uri, maxDim, quality) }
                if (d != null) latestImage(d)
            }
        }
    }

    // TakePicture + FileProvider：MIUI 相机这种模式下拍完**自动返回**，
    // 不会像 TakePicturePreview 那样停在预览页等手动确认。
    val camera = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { ok: Boolean ->
        val uri = cameraUri
        if (!ok || uri == null) {
            // 用户取消拍照，不用提示
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val d = withContext(Dispatchers.IO) { ImagePicker.toDataUri(ctx, uri, maxDim, quality) }
            if (d != null) {
                latestImage(d)
            } else {
                latestNotice("照片没拿到（相机没写入文件），改用「相册」选图试试")
            }
        }
    }

    val documents = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val att = withContext(Dispatchers.IO) { FilePicker.read(ctx, uri) }
                if (att != null) {
                    if (att.kind == "image" && att.dataUri.isNotBlank()) {
                        latestImage(att.dataUri)
                    } else {
                        latestFile(att)
                    }
                }
            }
        }
    }

    return MediaPicker(
        pickGallery = { gallery.launch("image/*") },
        pickFile = {
            documents.launch(
                arrayOf(
                    "text/*",
                    "application/json",
                    "application/xml",
                    "application/javascript",
                    "application/octet-stream",
                    "*/*",
                )
            )
        },
        takePhoto = {
            try {
                val dir = File(ctx.cacheDir, "camera")
                if (!dir.exists()) dir.mkdirs()
                // 清掉上一轮残留
                dir.listFiles()?.forEach { it.delete() }
                val f = File(dir, "shot_" + System.currentTimeMillis() + ".jpg")
                f.createNewFile()
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                cameraUri = uri
                camera.launch(uri)
            } catch (e: Exception) {
                latestNotice("启动相机失败：" + (e.message ?: e.javaClass.simpleName))
            }
        },
    )
}

/** 来源弹窗：拍照 / 相册 / 文件。 */
@Composable
fun ImageSourceSheet(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDismiss: () -> Unit,
    onFile: (() -> Unit)? = null,
) {
    val br = brand
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(br.card, RoundedCornerShape(20.dp))
                .padding(16.dp),
        ) {
            Text("添加内容", color = br.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (onFile != null) "图片会自动压缩；代码/文本文件会把正文读出来一起发给模型"
                else "会自动压到长边 1280 再发给接口",
                color = br.textSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceOption("相册", "最快，推荐", Icons.Filled.List, Modifier.weight(1f), onGallery)
                SourceOption("拍照", "系统相机", Icons.Filled.Face, Modifier.weight(1f), onCamera)
                if (onFile != null) {
                    SourceOption("文件", "代码/文本", Icons.Filled.Create, Modifier.weight(1f), onFile)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "支持 .py .kt .java .js .json .md .txt .log .yml .sql .sh 等文本类文件（≤200KB）；" +
                    "apk、zip、pdf、音视频不在接口支持范围内。" +
                    "\n拍照要等系统相机冷启动（首次 1-3 秒），拍完会自动回到这里。",
                color = br.textSecondary,
                fontSize = 10.sp,
            )

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .background(br.chipBg, RoundedCornerShape(10.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("取消", color = br.chipText, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SourceOption(
    title: String,
    desc: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val br = brand
    Column(
        modifier = modifier
            .background(br.chipBg, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Brush.linearGradient(br.gradient), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = br.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(desc, color = br.textSecondary, fontSize = 10.sp)
    }
}
