package com.seagull.agnesearch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 品牌色板：不依赖 Material3 的语义色，保证换版本也不会跑形。 */
data class BrandColors(
    val bg: Color,
    val bgAlt: Color,
    val gradient: List<Color>,
    val card: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val chipBg: Color,
    val chipText: Color,
    val codeBg: Color,
    val divider: Color,
    val danger: Color,
)

val LightBrand = BrandColors(
    bg = Color(0xFFF5F6FB),
    bgAlt = Color(0xFFFFFFFF),
    gradient = listOf(Color(0xFF5B7CFA), Color(0xFF9B6BFF), Color(0xFF49C7F5)),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE5E8F3),
    textPrimary = Color(0xFF0F1218),
    textSecondary = Color(0xFF6B7280),
    accent = Color(0xFF4C6EF5),
    onAccent = Color(0xFFFFFFFF),
    chipBg = Color(0xFFEEF1FE),
    chipText = Color(0xFF4459CC),
    codeBg = Color(0xFFF1F3F9),
    divider = Color(0xFFE7EAF3),
    danger = Color(0xFFE5484D),
)

val DarkBrand = BrandColors(
    bg = Color(0xFF07080D),
    bgAlt = Color(0xFF0E1017),
    gradient = listOf(Color(0xFF6D8BFF), Color(0xFFB06CFF), Color(0xFF3ED0F0)),
    card = Color(0xFF12141C),
    cardBorder = Color(0xFF232838),
    textPrimary = Color(0xFFEDEFF7),
    textSecondary = Color(0xFF98A1B2),
    accent = Color(0xFF7B93FF),
    onAccent = Color(0xFF0A0C12),
    chipBg = Color(0xFF1A1F2E),
    chipText = Color(0xFFA9BAFF),
    codeBg = Color(0xFF171A24),
    divider = Color(0xFF232838),
    danger = Color(0xFFFF6369),
)

val LocalBrand = staticCompositionLocalOf { LightBrand }

/** 当前品牌色，UI 里到处都用它。 */
val brand: BrandColors
    @Composable get() = LocalBrand.current

private val AppTypography = Typography(
    displaySmall = Typography().displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 34.sp),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = Typography().bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
)

@Composable
fun AgnesTheme(
    /** system | light | dark */
    mode: String = "system",
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val br = if (dark) DarkBrand else LightBrand

    val scheme = if (dark) {
        darkColorScheme(
            primary = br.accent,
            onPrimary = br.onAccent,
            background = br.bg,
            onBackground = br.textPrimary,
            surface = br.card,
            onSurface = br.textPrimary,
            surfaceVariant = br.chipBg,
            onSurfaceVariant = br.chipText,
            outline = br.cardBorder,
            error = br.danger,
        )
    } else {
        lightColorScheme(
            primary = br.accent,
            onPrimary = br.onAccent,
            background = br.bg,
            onBackground = br.textPrimary,
            surface = br.card,
            onSurface = br.textPrimary,
            surfaceVariant = br.chipBg,
            onSurfaceVariant = br.chipText,
            outline = br.cardBorder,
            error = br.danger,
        )
    }

    CompositionLocalProvider(LocalBrand provides br) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            content = content,
        )
    }
}
