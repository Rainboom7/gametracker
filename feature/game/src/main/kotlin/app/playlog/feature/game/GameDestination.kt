package app.playlog.feature.game

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.playlog.core.model.GameId
import kotlinx.serialization.Serializable

@Serializable
data class GameDestination(val gameId: String)

fun NavController.navigateToGame(id: GameId) = navigate(GameDestination(id.encode()))

fun NavGraphBuilder.gameScreen(
    onBack: () -> Unit,
    onOpenAllScreenshots: (GameId) -> Unit,
) {
    composable<GameDestination> {
        GameDetailRoute(onBack = onBack, onOpenAllScreenshots = onOpenAllScreenshots)
    }
}
