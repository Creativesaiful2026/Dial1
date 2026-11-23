package com.example.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.datastore.DialMeConfig
import com.example.core.datastore.FontFamilyOption
import com.example.core.datastore.ThemeMode

val LocalDialMeConfig = staticCompositionLocalOf { DialMeConfig() }

val PlusJakartaFontFamily = FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal))
val SpaceGroteskFontFamily = FontFamily(Font(R.font.space_grotesk, FontWeight.Normal))
val JetBrainsMonoFontFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

fun parseHexColor(hex: String, fallback: Color): Color {
    return runCatching {
        val clean = hex.trim().removePrefix("#")
        val value = clean.toLong(16)
        when (clean.length) {
            6 -> Color(0xFF000000L or value)
            8 -> Color(value)
            else -> fallback
        }
    }.getOrDefault(fallback)
}

fun resolveFontFamily(option: FontFamilyOption): FontFamily = when (option) {
    FontFamilyOption.PLUS_JAKARTA -> PlusJakartaFontFamily
    FontFamilyOption.SPACE_GROTESK -> SpaceGroteskFontFamily
    FontFamilyOption.JETBRAINS_MONO -> JetBrainsMonoFontFamily
    FontFamilyOption.SYSTEM -> FontFamily.Default
}

@Composable
fun DialMeTheme(
    config: DialMeConfig = DialMeConfig(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val useDark = when (config.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val isAmoled = config.themeMode == ThemeMode.AMOLED

    val primaryAccent = parseHexColor(config.accentHex, Color(0xFF14B8A6))
    val secondaryAccent = parseHexColor(config.secondaryAccentHex, Color(0xFF3B82F6))

    val baseScheme: ColorScheme = if (config.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (useDark) {
        darkColorScheme(
            primary = primaryAccent,
            onPrimary = if (primaryAccent.luminance() > 0.5f) Color(0xFF041214) else Color(0xFFFFFFFF),
            primaryContainer = primaryAccent.copy(alpha = 0.22f),
            onPrimaryContainer = Color(0xFFE0F7F4),
            secondary = secondaryAccent,
            onSecondary = Color.White,
            secondaryContainer = secondaryAccent.copy(alpha = 0.22f),
            onSecondaryContainer = Color(0xFFDBEAFE),
            tertiary = Color(0xFFF59E0B),
            background = if (isAmoled) Color.Black else Color(0xFF090E17),
            onBackground = Color(0xFFF1F5F9),
            surface = if (isAmoled) Color.Black else Color(0xFF0F1623),
            onSurface = Color(0xFFF1F5F9),
            surfaceVariant = if (isAmoled) Color(0xFF0B0E14) else Color(0xFF182235),
            onSurfaceVariant = Color(0xFF94A3B8),
            surfaceContainerLowest = if (isAmoled) Color.Black else Color(0xFF0B111D),
            surfaceContainerLow = if (isAmoled) Color(0xFF080B10) else Color(0xFF121B2B),
            surfaceContainer = if (isAmoled) Color(0xFF0D1117) else Color(0xFF162033),
            surfaceContainerHigh = if (isAmoled) Color(0xFF141922) else Color(0xFF1E293B),
            outline = Color(0xFF334155),
            error = Color(0xFFEF4444),
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = primaryAccent,
            onPrimary = Color.White,
            primaryContainer = primaryAccent.copy(alpha = 0.14f),
            onPrimaryContainer = Color(0xFF042F2E),
            secondary = secondaryAccent,
            onSecondary = Color.White,
            secondaryContainer = secondaryAccent.copy(alpha = 0.14f),
            onSecondaryContainer = Color(0xFF1E3A8A),
            tertiary = Color(0xFFD97706),
            background = Color(0xFFF6F8FC),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFE8EEF7),
            onSurfaceVariant = Color(0xFF475569),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF1F5F9),
            surfaceContainer = Color(0xFFE8EEF7),
            surfaceContainerHigh = Color(0xFFDDE5F0),
            outline = Color(0xFFCBD5E1),
            error = Color(0xFFDC2626),
            onError = Color.White
        )
    }

    val colorScheme = if (isAmoled) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF06080C)
        )
    } else {
        baseScheme
    }

    val font = resolveFontFamily(config.fontFamily)
    val scale = config.fontScale.coerceIn(0.85f, 1.3f)

    val typography = Typography(
        displayLarge = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = (36 * scale).sp, lineHeight = (44 * scale).sp),
        headlineMedium = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp),
        titleLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = (20 * scale).sp, lineHeight = (28 * scale).sp),
        titleMedium = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = (16 * scale).sp, lineHeight = (22 * scale).sp),
        bodyLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.Normal, fontSize = (15 * scale).sp, lineHeight = (22 * scale).sp),
        bodyMedium = TextStyle(fontFamily = font, fontWeight = FontWeight.Normal, fontSize = (13.5f * scale).sp, lineHeight = (20 * scale).sp),
        labelLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = (13.5f * scale).sp, lineHeight = (18 * scale).sp),
        labelSmall = TextStyle(fontFamily = font, fontWeight = FontWeight.Medium, fontSize = (11 * scale).sp, lineHeight = (14 * scale).sp)
    )

    val radius = config.dialpadCornerRadiusDp.coerceIn(8, 32).dp
    val shapes = Shapes(
        small = RoundedCornerShape((radius.value * 0.55f).dp),
        medium = RoundedCornerShape((radius.value * 0.8f).dp),
        large = RoundedCornerShape(radius)
    )

    CompositionLocalProvider(LocalDialMeConfig provides config) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}
