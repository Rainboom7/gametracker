package app.playlog.feature.screenshots

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.playlog.core.model.GameId
import kotlinx.serialization.Serializable

/** [gameId] narrows the gallery to one title ("All N" on the game page). */
@Serializable
data class ScreenshotsDestination(val gameId: String? = null)

fun NavController.navigateToScreenshots(gameId: GameId?) = navigate(ScreenshotsDestination(gameId?.encode()))

fun NavGraphBuilder.screenshotsScreen(onOpenGame: (GameId) -> Unit, onClearFilter: () -> Unit) {
    composable<ScreenshotsDestination> {
        ScreenshotsRoute(onOpenGame = onOpenGame, onClearFilter = onClearFilter)
    }
}
