package app.playlog.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import app.playlog.core.model.Platform

/** Accent, badge text and badge background for [platform]. */
@Composable
@ReadOnlyComposable
fun platformColors(platform: Platform): PlatformColors {
    val palette = LocalPlatformPalette.current
    return when (platform) {
        Platform.STEAM -> palette.steam
        Platform.PLAYSTATION -> palette.playstation
        Platform.NINTENDO -> palette.nintendo
    }
}

/** Short label used in badges and monograms. */
val Platform.monogram: String
    get() = when (this) {
        Platform.STEAM -> "S"
        Platform.PLAYSTATION -> "PS"
        Platform.NINTENDO -> "N"
    }
