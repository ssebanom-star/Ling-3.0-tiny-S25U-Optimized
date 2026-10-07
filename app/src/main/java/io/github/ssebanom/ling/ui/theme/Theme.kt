package io.github.ssebanom.ling.ui.theme

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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ssebanom.ling.tools.ToolGroup
import io.github.ssebanom.ling.ui.markdown.CodePalette

private val Indigo = Color(0xFF5B5BD6)
private val IndigoLight = Color(0xFFB4B6FF)

private val LightScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E2FF),
    onPrimaryContainer = Color(0xFF1B1A6B),
    secondary = Color(0xFF0E8C7E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDF3EC),
    onSecondaryContainer = Color(0xFF00382F),
    tertiary = Color(0xFFB2541A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC7),
    onTertiaryContainer = Color(0xFF3A1400),
    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF16171C),
    surface = Color(0xFFFAFAFC),
    onSurface = Color(0xFF16171C),
    surfaceVariant = Color(0xFFE6E6EE),
    onSurfaceVariant = Color(0xFF5A5C68),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F4F8),
    surfaceContainer = Color(0xFFEFEFF5),
    surfaceContainerHigh = Color(0xFFE9E9F0),
    surfaceContainerHighest = Color(0xFFE3E3EB),
    outline = Color(0xFF8D8F9C),
    outlineVariant = Color(0xFFD6D6E0),
    error = Color(0xFFC62B3B),
    errorContainer = Color(0xFFFFDADB),
    onErrorContainer = Color(0xFF410008),
)

private val DarkScheme = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF1E1D73),
    primaryContainer = Color(0xFF34348F),
    onPrimaryContainer = Color(0xFFE3E2FF),
    secondary = Color(0xFF6FD9C7),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF0B4F46),
    onSecondaryContainer = Color(0xFFBDF5EA),
    tertiary = Color(0xFFFFB68D),
    onTertiary = Color(0xFF552100),
    tertiaryContainer = Color(0xFF7A3507),
    onTertiaryContainer = Color(0xFFFFDCC7),
    background = Color(0xFF0E0F13),
    onBackground = Color(0xFFE6E6EC),
    surface = Color(0xFF0E0F13),
    onSurface = Color(0xFFE6E6EC),
    surfaceVariant = Color(0xFF2A2C35),
    onSurfaceVariant = Color(0xFFA9ABB8),
    surfaceContainerLowest = Color(0xFF0A0B0E),
    surfaceContainerLow = Color(0xFF15161B),
    surfaceContainer = Color(0xFF1A1B21),
    surfaceContainerHigh = Color(0xFF212229),
    surfaceContainerHighest = Color(0xFF2A2B33),
    outline = Color(0xFF6E7080),
    outlineVariant = Color(0xFF34363F),
    error = Color(0xFFFF8A93),
    errorContainer = Color(0xFF5C1119),
    onErrorContainer = Color(0xFFFFDADB),
)

private val base = Typography()
private val LingTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(lineHeight = 25.sp, letterSpacing = 0.1.sp),
    bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val LingShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val LocalDark = staticCompositionLocalOf { false }

@Composable
fun LingTheme(themeMode: Int = 0, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && dark -> dynamicDarkColorScheme(ctx)
        dynamicColor -> dynamicLightColorScheme(ctx)
        dark -> DarkScheme
        else -> LightScheme
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalDark provides dark) {
        MaterialTheme(colorScheme = scheme, typography = LingTypography, shapes = LingShapes, content = content)
    }
}

object LingColors {
    val isDark: Boolean @Composable @ReadOnlyComposable get() = LocalDark.current

    /** 툴 묶음별 색(차트·아이콘) */
    @Composable
    @ReadOnlyComposable
    fun group(g: ToolGroup?): Color {
        val d = LocalDark.current
        return when (g) {
            ToolGroup.BASIC -> if (d) Color(0xFF9FA8FF) else Color(0xFF5B5BD6)
            ToolGroup.WEB -> if (d) Color(0xFF6FD9C7) else Color(0xFF0E8C7E)
            ToolGroup.DEVICE -> if (d) Color(0xFFFFB68D) else Color(0xFFB2541A)
            ToolGroup.FILES -> if (d) Color(0xFFF5D06B) else Color(0xFF9A6D00)
            ToolGroup.SCREEN -> if (d) Color(0xFFF59CC8) else Color(0xFFB0306E)
            null -> if (d) Color(0xFFA9ABB8) else Color(0xFF5A5C68)
        }
    }

    val success: Color @Composable @ReadOnlyComposable get() = if (LocalDark.current) Color(0xFF7BDCA0) else Color(0xFF1C8A4C)
    val warning: Color @Composable @ReadOnlyComposable get() = if (LocalDark.current) Color(0xFFF5C26B) else Color(0xFFA86A00)

    @Composable
    @ReadOnlyComposable
    fun code(): CodePalette = if (LocalDark.current || MaterialTheme.colorScheme.surface.luminance() < 0.3f) CodePalette(
        bg = Color(0xFF16171D), fg = Color(0xFFDDDEE6), keyword = Color(0xFFC792EA), string = Color(0xFFA5D6A7),
        comment = Color(0xFF7C7F8E), number = Color(0xFFF7A26B), type = Color(0xFF82AAFF), fn = Color(0xFF7FDBCA),
    ) else CodePalette(
        bg = Color(0xFFF1F1F6), fg = Color(0xFF24252B), keyword = Color(0xFF8A2BB8), string = Color(0xFF237A3B),
        comment = Color(0xFF8B8D98), number = Color(0xFFB4530F), type = Color(0xFF2357C7), fn = Color(0xFF0E7C86),
    )
}

val MonoSmall = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
