package com.seagull.agnesearch.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seagull.agnesearch.ui.theme.brand

/** 全屏背景：渐变底 + 两团彩色光斑，深色下也不脏。 */
@Composable
fun BrandBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val br = brand
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(br.bg, br.bgAlt, br.bg))),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(br.gradient[0].copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(w * 0.12f, -w * 0.10f),
                    radius = w * 0.85f,
                ),
                radius = w * 0.85f,
                center = Offset(w * 0.12f, -w * 0.10f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(br.gradient[1].copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(w * 0.95f, w * 0.28f),
                    radius = w * 0.70f,
                ),
                radius = w * 0.70f,
                center = Offset(w * 0.95f, w * 0.28f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(br.gradient[2].copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(w * 0.30f, size.height * 0.85f),
                    radius = w * 0.60f,
                ),
                radius = w * 0.60f,
                center = Offset(w * 0.30f, size.height * 0.85f),
            )
        }
        content()
    }
}

/** 毛玻璃感卡片。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Int = 20,
    padding: Int = 16,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val br = brand
    val shape = RoundedCornerShape(corner.dp)
    var m = modifier
        .fillMaxWidth()
        .background(br.card, shape)
        .border(BorderStroke(1.dp, br.cardBorder), shape)
    if (onClick != null) m = m.clickable { onClick() }
    Box(modifier = m.padding(padding.dp)) {
        content()
    }
}

/** 顶部栏，自己画，避开 experimental API。 */
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val br = brand
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = br.textPrimary)
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            color = br.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** 自绘胶囊标签。 */
@Composable
fun Pill(
    text: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val br = brand
    val shape = CircleShape
    val bg = if (selected) br.accent else br.chipBg
    val fg = if (selected) br.onAccent else br.chipText
    Box(
        modifier = Modifier
            .background(bg, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text = text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** 渐变主按钮。 */
@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val br = brand
    val shape = RoundedCornerShape(16.dp)
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier = modifier
            .background(
                Brush.horizontalGradient(br.gradient.map { it.copy(alpha = alpha) }),
                shape,
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/** 细进度条，自己画。 */
@Composable
fun ThinProgress(fraction: Float, modifier: Modifier = Modifier) {
    val br = brand
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(br.divider, CircleShape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .background(Brush.horizontalGradient(br.gradient), CircleShape),
        )
    }
}

/** 小圆点徽标。 */
@Composable
fun DotBadge(color: Color? = null) {
    val c = color ?: brand.accent
    Box(
        modifier = Modifier
            .size(7.dp)
            .background(c, CircleShape),
    )
}

/** 圆角图标底座。 */
@Composable
fun IconBadge(icon: ImageVector, tint: Color? = null, size: Int = 38) {
    val br = brand
    val t = tint ?: br.accent
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(br.chipBg, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = t, modifier = Modifier.size((size * 0.5).dp))
    }
}

/** 空状态。 */
@Composable
fun EmptyState(title: String, desc: String, icon: ImageVector) {
    val br = brand
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, size = 56)
        Spacer(Modifier.height(14.dp))
        Text(title, color = br.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(desc, color = br.textSecondary, fontSize = 13.sp)
    }
}

/** 分区标题。 */
@Composable
fun SectionTitle(text: String, trailing: String = "") {
    val br = brand
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = br.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        if (trailing.isNotEmpty()) {
            Text(trailing, color = br.textSecondary, fontSize = 12.sp)
        }
    }
}

/** 通用信息行（设置页用）。 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    val br = brand
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = br.textPrimary, fontSize = 14.sp, modifier = Modifier.width(96.dp))
        Text(
            value.ifEmpty { "未设置" },
            color = if (value.isEmpty()) br.textSecondary else br.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 圆角方形头像字。 */
@Composable
fun LetterAvatar(letter: String, size: Int = 52) {
    val br = brand
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(Brush.linearGradient(br.gradient), RoundedCornerShape(size / 3)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size / 2.4).sp)
    }
}

/** 卡片内的小分隔线。 */
@Composable
fun ThinDivider() {
    val br = brand
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(br.divider),
    )
}

/** 统一的内边距常量，避免每页各写一套。 */
object Pad {
    val screen = PaddingValues(horizontal = 16.dp)
}
