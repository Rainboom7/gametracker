package app.playlog.core.designsystem.theme

// Playlog design tokens, from the design handoff (design/design-spec.md, compose/Theme.kt).

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.playlog.core.designsystem.R

// ---- Colors ----
object PlaylogColors {
    val Background = Color(0xFF0D0E12)
    val Surface = Color(0xFF16181F)
    val SurfaceVariant = Color(0xFF1F222B)
    val Outline = Color(0xFF2C303B)
    val OutlineStrong = Color(0xFF3A3F4C)
    val Divider = Color(0xFF23262F)
    val OnSurface = Color(0xFFF1F2F5)
    val OnSurfaceVariant = Color(0xFFA3A9B7)
    val Lime = Color(0xFFC8F55A)
    val OnLime = Color(0xFF0D0E12)
    val LimeContainer = Color(0xFF24301A)
    val Warning = Color(0xFFFFB86B)

    // Playful accents for home cards, stat bubbles and tab indicators. Lime stays the brand color.
    val Violet = Color(0xFFB69CFF)
    val Pink = Color(0xFFFF7EB6)
    val Sky = Color(0xFF5CD0FF)
    val Orange = Color(0xFFFFA24C)
    val Mint = Color(0xFF5EE6B0)

    // Light theme (secondary; dark is the default)
    val LightBackground = Color(0xFFF6F7F9)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFEEF0F3)
    val LightOutline = Color(0xFFD9DCE2)
    val LightOnSurface = Color(0xFF111318)
    val LightOnSurfaceVariant = Color(0xFF5B6170)
    val LimeDark = Color(0xFF4E6B00) // primary on light backgrounds (passes 4.5:1)
}

@Immutable
data class PlatformColors(
    val accent: Color,      // bars, dots, logo tile
    val badgeText: Color,   // text on badgeBg
    val badgeBg: Color,
)

@Immutable
data class PlatformPalette(
    val steam: PlatformColors = SteamColors,
    val playstation: PlatformColors = PlayStationColors,
    val nintendo: PlatformColors = NintendoColors,
)

private val SteamColors = PlatformColors(Color(0xFF66C0F4), Color(0xFF66C0F4), Color(0xFF16233A))
private val PlayStationColors = PlatformColors(Color(0xFF6F97FF), Color(0xFF8FAEFF), Color(0xFF1A2142))
private val NintendoColors = PlatformColors(Color(0xFFFF6B6B), Color(0xFFFF8A8A), Color(0xFF2A1719))

val LocalPlatformPalette = staticCompositionLocalOf { PlatformPalette() }

private val DarkScheme = darkColorScheme(
    primary = PlaylogColors.Lime,
    onPrimary = PlaylogColors.OnLime,
    primaryContainer = PlaylogColors.LimeContainer,
    onPrimaryContainer = PlaylogColors.Lime,
    background = PlaylogColors.Background,
    onBackground = PlaylogColors.OnSurface,
    surface = PlaylogColors.Surface,
    onSurface = PlaylogColors.OnSurface,
    surfaceVariant = PlaylogColors.SurfaceVariant,
    onSurfaceVariant = PlaylogColors.OnSurfaceVariant,
    surfaceContainer = PlaylogColors.Surface,
    surfaceContainerHigh = PlaylogColors.SurfaceVariant,
    outline = PlaylogColors.OutlineStrong,
    outlineVariant = PlaylogColors.Outline,
    error = PlaylogColors.Warning,
    inverseSurface = PlaylogColors.OnSurface,
    inverseOnSurface = PlaylogColors.Background,
)

private val LightScheme = lightColorScheme(
    primary = PlaylogColors.LimeDark,
    onPrimary = Color.White,
    primaryContainer = PlaylogColors.Lime,
    onPrimaryContainer = PlaylogColors.OnLime,
    background = PlaylogColors.LightBackground,
    onBackground = PlaylogColors.LightOnSurface,
    surface = PlaylogColors.LightSurface,
    onSurface = PlaylogColors.LightOnSurface,
    surfaceVariant = PlaylogColors.LightSurfaceVariant,
    onSurfaceVariant = PlaylogColors.LightOnSurfaceVariant,
    outlineVariant = PlaylogColors.LightOutline,
)

// ---- Typography ----
// Manrope (headings, numbers) + Inter (text), bundled as variable TTFs: both cover Cyrillic.
@OptIn(ExperimentalTextApi::class) // Font(resId, …, variationSettings)
private fun manrope(weight: Int) = Font(
    R.font.manrope_variable,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter_variable,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(manrope(500), manrope(600), manrope(700), manrope(800))
val Inter = FontFamily(inter(400), inter(500), inter(600))

val PlaylogTypography = Typography(
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp), // StatTile value
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
)

// ---- Shapes & spacing ----
val PlaylogShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),  // platform badge
    small = RoundedCornerShape(12.dp),      // cover, icon button, secondary button
    medium = RoundedCornerShape(16.dp),     // stat tile, primary button
    large = RoundedCornerShape(20.dp),      // list cards
    extraLarge = RoundedCornerShape(24.dp),
)

object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp   // screen horizontal padding
    val xl = 24.dp
    val xxl = 32.dp
}

@Composable
// Dark is the product default; pass isSystemInDarkTheme() once a "follow system" setting exists.
fun PlaylogTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPlatformPalette provides PlatformPalette()) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = PlaylogTypography,
            shapes = PlaylogShapes,
            content = content,
        )
    }
}
