package com.coursetable.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class ThemeMode(val key: String, val label: String) {
    AUTO("auto", "自动"), LIGHT("light", "浅色"), DARK("dark", "深色");
    companion object { fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: AUTO }
}

@Immutable
data class LiquidColorScheme(
    val primary: Color, val onPrimary: Color, val primaryContainer: Color, val onPrimaryContainer: Color,
    val background: Color, val onSurface: Color, val surface: Color, val onSurfaceVariant: Color,
    val outlineVariant: Color, val surfaceContainerHigh: Color, val surfaceContainerHighest: Color,
    val error: Color, val glass: Color, val glassBorder: Color, val isDark: Boolean
)

@Immutable
data class LiquidTypography(
    val headlineSmall: TextStyle, val titleLarge: TextStyle, val titleMedium: TextStyle,
    val titleSmall: TextStyle, val bodyLarge: TextStyle, val bodyMedium: TextStyle,
    val bodySmall: TextStyle, val labelLarge: TextStyle, val labelMedium: TextStyle, val labelSmall: TextStyle
)

@Immutable data class LiquidShapes(val medium: RoundedCornerShape)

private val AppTypography = LiquidTypography(
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
)
private val AppShapes = LiquidShapes(RoundedCornerShape(20.dp))

private fun lightScheme(accent: Color, soft: Color) = LiquidColorScheme(
    accent, Color.White, soft, Color(0xFF101114), Color(0xFFF4F6FB), Color(0xFF111114),
    Color(0xE6FFFFFF), Color(0xFF65666D), Color(0x2A6D7180), Color(0xC7FFFFFF),
    Color(0xFFF0F2F7), Color(0xFFFF3B30), Color(0xB0FFFFFF), Color(0x78FFFFFF), false
)
private fun darkScheme(accent: Color, soft: Color) = LiquidColorScheme(
    accent, Color(0xFF07101F), soft, Color.White, Color(0xFF080A0F), Color(0xFFF5F5F7),
    Color(0xD81C1D22), Color(0xFFA0A1AA), Color(0x458E8E93), Color(0xC426282E),
    Color(0xFF343740), Color(0xFFFF453A), Color(0x9E282B33), Color(0x3FFFFFFF), true
)

enum class ThemeColor(
    val key: String, val label: String, val swatch: Color,
    val lightScheme: LiquidColorScheme, val darkScheme: LiquidColorScheme
) {
    BLUE("blue", "蓝色", Color(0xFF0A84FF), lightScheme(Color(0xFF007AFF), Color(0xFFDCEEFF)), darkScheme(Color(0xFF0A84FF), Color(0xFF15385F))),
    GREEN("green", "绿色", Color(0xFF30D158), lightScheme(Color(0xFF248A3D), Color(0xFFDEF5E4)), darkScheme(Color(0xFF30D158), Color(0xFF173D22))),
    PURPLE("purple", "紫色", Color(0xFFBF5AF2), lightScheme(Color(0xFF8944AB), Color(0xFFF1E3F8)), darkScheme(Color(0xFFBF5AF2), Color(0xFF452052))),
    ORANGE("orange", "橙色", Color(0xFFFF9F0A), lightScheme(Color(0xFFC93400), Color(0xFFFFE8D7)), darkScheme(Color(0xFFFF9F0A), Color(0xFF543010))),
    PINK("pink", "粉色", Color(0xFFFF375F), lightScheme(Color(0xFFD30F45), Color(0xFFFFE0E8)), darkScheme(Color(0xFFFF375F), Color(0xFF55182A)));
    companion object { fun fromKey(key: String?): ThemeColor = entries.firstOrNull { it.key == key } ?: BLUE }
}

private val LocalColors = staticCompositionLocalOf { ThemeColor.BLUE.lightScheme }
private val LocalTypography = staticCompositionLocalOf { AppTypography }
private val LocalShapes = staticCompositionLocalOf { AppShapes }

object LiquidTheme {
    val colorScheme: LiquidColorScheme @Composable get() = LocalColors.current
    val typography: LiquidTypography @Composable get() = LocalTypography.current
    val shapes: LiquidShapes @Composable get() = LocalShapes.current
}

val CourseColorPalette = listOf(
    Color(0xFF0A84FF), Color(0xFF64D2FF), Color(0xFF30B0C7), Color(0xFF30D158),
    Color(0xFFFFD60A), Color(0xFFFF9F0A), Color(0xFFFF453A), Color(0xFFFF375F),
    Color(0xFFBF5AF2), Color(0xFF5E5CE6), Color(0xFFA2845E), Color(0xFF8E8E93)
)

fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
fun Color.Companion.fromStoredLong(stored: Long): Color {
    val argb = stored.toInt()
    return if ((argb ushr 24) and 0xFF == 0) CourseColorPalette[0] else Color(argb)
}

@Composable
fun CourseTableTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    themeColor: ThemeColor = ThemeColor.BLUE,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(
        LocalColors provides if (dark) themeColor.darkScheme else themeColor.lightScheme,
        LocalTypography provides AppTypography,
        LocalShapes provides AppShapes,
        content = content
    )
}
