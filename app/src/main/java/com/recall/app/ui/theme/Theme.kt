package com.recall.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.recall.app.data.Accent
import com.recall.app.data.ThemeMode

/** A soft tinted background with a matching stronger foreground, for icon tiles and pills. */
@Immutable
data class Tone(val bg: Color, val fg: Color)

/**
 * The app's own colour system. Material's scheme is derived from it so stock components
 * (pickers, dialogs, switches) match, but screens read from here directly.
 */
@Immutable
data class Palette(
    val dark: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val outline: Color,
    val primary: Color,
    val primaryDeep: Color,
    val primarySoft: Color,
    val onPrimary: Color,
    val shadow: Color,
    val violet: Tone,
    val rose: Tone,
    val blue: Tone,
    val amber: Tone,
    val green: Tone,
    val pink: Tone,
) {
    val background: Brush get() = Brush.verticalGradient(listOf(bgTop, bgBottom, bgBottom))
}

private fun accentColor(accent: Accent, dark: Boolean): Color = when (accent) {
    Accent.VIOLET -> if (dark) Color(0xFFAB9BFF) else Color(0xFF6B4EE6)
    Accent.INDIGO -> if (dark) Color(0xFF9CABFF) else Color(0xFF4458DB)
    Accent.TEAL -> if (dark) Color(0xFF6ED3C4) else Color(0xFF17857A)
    Accent.ROSE -> if (dark) Color(0xFFFF9CC2) else Color(0xFFCF4580)
}

fun palette(accent: Accent, dark: Boolean): Palette {
    val primary = accentColor(accent, dark)
    return if (!dark) {
        val surface = Color.White
        Palette(
            dark = false,
            bgTop = lerp(Color(0xFFFCFAF7), primary, 0.07f),
            bgBottom = Color(0xFFFCFAF7),
            surface = surface,
            surfaceSoft = lerp(Color(0xFFF7F5F2), primary, 0.04f),
            ink = Color(0xFF1B1730),
            inkSoft = Color(0xFF5F5B73),
            inkFaint = Color(0xFF8E8AA0),
            outline = Color(0xFFEAE6F0),
            primary = primary,
            primaryDeep = lerp(primary, Color.Black, 0.25f),
            primarySoft = lerp(surface, primary, 0.12f),
            onPrimary = Color.White,
            shadow = lerp(primary, Color(0xFF3A2E6B), 0.5f),
            violet = Tone(Color(0xFFECE6FF), Color(0xFF6B4EE6)),
            rose = Tone(Color(0xFFFFE4E6), Color(0xFFE0454B)),
            blue = Tone(Color(0xFFE2EDFF), Color(0xFF3D7BF0)),
            amber = Tone(Color(0xFFFFF0D4), Color(0xFFC77A00)),
            green = Tone(Color(0xFFDFF5E6), Color(0xFF1E9552)),
            pink = Tone(Color(0xFFFBE5F5), Color(0xFFBE46A6)),
        )
    } else {
        val surface = Color(0xFF1E1B2B)
        fun tone(fg: Color) = Tone(fg.copy(alpha = 0.16f).compositeOver(surface), fg)
        Palette(
            dark = true,
            bgTop = lerp(Color(0xFF121019), primary, 0.08f),
            bgBottom = Color(0xFF121019),
            surface = surface,
            surfaceSoft = Color(0xFF282438),
            ink = Color(0xFFF2EFFA),
            inkSoft = Color(0xFFB9B4CC),
            inkFaint = Color(0xFF8A85A0),
            outline = Color(0xFF322D45),
            primary = primary,
            primaryDeep = lerp(primary, Color.White, 0.25f),
            primarySoft = lerp(surface, primary, 0.20f),
            onPrimary = Color(0xFF16122B),
            shadow = Color.Transparent,
            violet = tone(Color(0xFFB7A8FF)),
            rose = tone(Color(0xFFFF8C91)),
            blue = tone(Color(0xFF8FB8FF)),
            amber = tone(Color(0xFFFFC660)),
            green = tone(Color(0xFF71DB9C)),
            pink = tone(Color(0xFFF29DDD)),
        )
    }
}

val LocalPalette = staticCompositionLocalOf { palette(Accent.VIOLET, false) }

object Recall {
    val colors: Palette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
}

// Google Sans where the device has it (Pixels), the system sans everywhere else.
private val brandFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map {
        Font(DeviceFontFamilyName("google-sans"), weight = it)
    }
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = brandFamily,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
)

// Sizes are in sp throughout, so the system font-size setting scales everything.
private val typography = Typography(
    displaySmall = style(32, 38, FontWeight.Bold, -0.02f),
    headlineLarge = style(30, 36, FontWeight.Bold, -0.02f),
    headlineMedium = style(26, 32, FontWeight.Bold, -0.015f),
    headlineSmall = style(22, 28, FontWeight.SemiBold, -0.01f),
    titleLarge = style(20, 26, FontWeight.SemiBold, -0.01f),
    titleMedium = style(17, 23, FontWeight.SemiBold),
    titleSmall = style(15, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 23, FontWeight.Normal),
    bodyMedium = style(15, 21, FontWeight.Normal),
    bodySmall = style(13, 18, FontWeight.Normal),
    labelLarge = style(15, 20, FontWeight.Medium),
    labelMedium = style(13, 17, FontWeight.Medium),
    labelSmall = style(12, 15, FontWeight.Medium),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun RecallTheme(mode: ThemeMode = ThemeMode.SYSTEM, accent: Accent = Accent.VIOLET, content: @Composable () -> Unit) {
    val p = palette(accent, isDark(mode))
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = p.primary, onPrimary = p.onPrimary,
            primaryContainer = p.primarySoft, onPrimaryContainer = p.primaryDeep,
            secondary = p.primary, onSecondary = p.onPrimary,
            secondaryContainer = p.primarySoft, onSecondaryContainer = p.primaryDeep,
            tertiary = p.pink.fg,
            background = p.bgBottom, onBackground = p.ink,
            surface = p.surface, onSurface = p.ink,
            surfaceVariant = p.surfaceSoft, onSurfaceVariant = p.inkSoft,
            surfaceTint = p.primary,
            surfaceContainerLowest = p.bgBottom, surfaceContainerLow = p.surface, surfaceContainer = p.surface,
            surfaceContainerHigh = p.surfaceSoft, surfaceContainerHighest = p.surfaceSoft,
            outline = p.inkFaint, outlineVariant = p.outline,
            error = p.rose.fg, onError = p.onPrimary, errorContainer = p.rose.bg, onErrorContainer = p.rose.fg,
            inverseSurface = p.ink, inverseOnSurface = p.bgBottom, inversePrimary = Color(0xFF6B4EE6),
        )
    } else {
        lightColorScheme(
            primary = p.primary, onPrimary = p.onPrimary,
            primaryContainer = p.primarySoft, onPrimaryContainer = p.primaryDeep,
            secondary = p.primary, onSecondary = p.onPrimary,
            secondaryContainer = p.primarySoft, onSecondaryContainer = p.primaryDeep,
            tertiary = p.pink.fg,
            background = p.bgBottom, onBackground = p.ink,
            surface = p.surface, onSurface = p.ink,
            surfaceVariant = p.surfaceSoft, onSurfaceVariant = p.inkSoft,
            surfaceTint = p.primary,
            surfaceContainerLowest = p.surface, surfaceContainerLow = p.surface, surfaceContainer = p.surface,
            surfaceContainerHigh = p.surface, surfaceContainerHighest = p.surfaceSoft,
            outline = p.inkFaint, outlineVariant = p.outline,
            error = p.rose.fg, onError = Color.White, errorContainer = p.rose.bg, onErrorContainer = p.rose.fg,
            inverseSurface = p.ink, inverseOnSurface = Color.White, inversePrimary = Color(0xFFCFC5FF),
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}
