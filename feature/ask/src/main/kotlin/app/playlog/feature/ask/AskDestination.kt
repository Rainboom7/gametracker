package app.playlog.feature.ask

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.playlog.core.model.GameId
import kotlinx.serialization.Serializable

/** [query] runs right away, e.g. "как Hades" from a game page. */
@Serializable
data class AskDestination(val query: String? = null)

fun NavController.navigateToAsk(query: String? = null) = navigate(AskDestination(query))

fun NavGraphBuilder.askScreen(onBack: () -> Unit, onOpenGame: (GameId) -> Unit) {
    composable<AskDestination> { AskRoute(onBack = onBack, onOpenGame = onOpenGame) }
}
