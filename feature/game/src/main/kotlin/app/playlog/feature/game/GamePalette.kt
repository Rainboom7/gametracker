package app.playlog.feature.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The cover's most vivid colour, lifted so it reads on the dark theme; null until the image is
 * loaded or when it has no usable colour (grey box art).
 */
@Composable
internal fun rememberCoverAccent(url: String?): Color? {
    val context = LocalContext.current
    val accent by produceState<Color?>(null, url) {
        if (url == null) return@produceState
        val request = ImageRequest.Builder(context).data(url).size(PaletteImageSize).allowHardware(false).build()
        val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap() ?: return@produceState
        // Palette walks every pixel; Default keeps that off the main thread.
        value = withContext(Dispatchers.Default) { Palette.from(bitmap).generate().accentArgb() }?.let(::Color)
    }
    return accent
}

private fun Palette.accentArgb(): Int? {
    val swatch = vibrantSwatch ?: lightVibrantSwatch ?: mutedSwatch ?: dominantSwatch ?: return null
    val hsl = swatch.hsl.copyOf()
    if (hsl[1] < MinSaturation) return null
    hsl[1] = hsl[1].coerceAtLeast(TargetSaturation)
    hsl[2] = hsl[2].coerceIn(MinLightness, MaxLightness)
    return ColorUtils.HSLToColor(hsl)
}

/** Re-themes the game page in [accent]: buttons, chips and progress take the cover's colour. */
@Composable
internal fun CoverTint(accent: Color?, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val primary by animateColorAsState(accent ?: scheme.primary, tween(TintMillis), label = "coverTint")
    // Lifted accents are light, so dark text stays readable on them.
    val onPrimary = if (ColorUtils.calculateLuminance(primary.toArgb()) > DarkTextFrom) scheme.background else Color.White
    MaterialTheme(
        colorScheme = scheme.copy(primary = primary, onPrimary = onPrimary),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

/** The page background just under the hero: [background] washed with a little of the accent. */
internal fun coverWash(background: Color, accent: Color?): Color = if (accent == null) background else lerp(background, accent, WashAmount)

private const val WashAmount = 0.14f
private const val PaletteImageSize = 160
private const val MinSaturation = 0.15f
private const val TargetSaturation = 0.55f
private const val MinLightness = 0.6f
private const val MaxLightness = 0.75f
private const val TintMillis = 600
private const val DarkTextFrom = 0.3
