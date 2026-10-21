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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.seagull.agnesearch.ui.theme.brand
import com.seagull.agnesearch.util.ImagePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 统一的图片来源：相册选图 / 相机拍照。
 * 拍照走 FileProvider 写 cacheDir，**不申请 CAMERA 权限**（用系统相机 App 拍）。
 * 结果一律转成 Data URI 回调出去。
 */
class ImageSourcePicker(
    val pickGallery: () -> Unit,
    val takePhoto: () -> Unit,
)

@Composable
fun rememberImageSourcePicker(
    maxDim: Int = 1280,
    quality: Int = 85,
    onPicked: (String) -> Unit,
): ImageSourcePicker {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onPicked)
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val gallery = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val d = withContext(Dispatchers.IO) { ImagePicker.toDataUri(ctx, uri, maxDim, quality) }
                if (d != null) latest(d)
            }
        }
    }

    val camera = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { ok: Boolean ->
        val uri = cameraUri
        if (ok && uri != null) {
            scope.launch {
                val d = withContext(Dispatchers.IO) { ImagePicker.toDataUri(ctx, uri, maxDim, quality) }
                if (d != null) latest(d)
            }
        }
    }

    return ImageSourcePicker(
        pickGallery = { gallery.launch("image/*") },
        takePhoto = {
            try {
                val dir = File(ctx.cacheDir, "camera")
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, "shot_" + System.currentTimeMillis() + ".jpg")
                f.createNewFile()
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                cameraUri = uri
                camera.launch(uri)
            } catch (e: Exception) {
                // 极少数 ROM 没有可用相机，静默失败
            }
        },
    )
}

/** 选图来源弹窗：拍照 / 相册。 */
@Composable
fun ImageSourceSheet(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDismiss: () -> Unit,
) {
    val br = brand
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(br.card, RoundedCornerShape(20.dp))
                .padding(16.dp),
        ) {
            Text("添加图片", color = br.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("会自动压到长边 1280 再发给接口", color = br.textSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceOption(
                    title = "拍照",
                    desc = "调用系统相机",
                    icon = Icons.Filled.Face,
                    modifier = Modifier.weight(1f),
                    onClick = onCamera,
                )
                SourceOption(
                    title = "相册",
                    desc = "从已有图片选",
                    icon = Icons.Filled.Search,
                    modifier = Modifier.weight(1f),
                    onClick = onGallery,
                )
            }

            Spacer(Modifier.height(14.dp))
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val br = brand
    Column(
        modifier = modifier
            .background(br.chipBg, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Brush.linearGradient(br.gradient), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = br.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(desc, color = br.textSecondary, fontSize = 10.sp)
    }
}
