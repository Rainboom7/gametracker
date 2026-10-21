package app.playlog.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.playlog.core.model.GameId
import kotlinx.serialization.Serializable

@Serializable
data object HomeDestination

/** Where the home screen's "see all" links and cards lead; the app wires them to tabs and routes. */
data class HomeActions(
    val onOpenGame: (GameId) -> Unit,
    val onOpenLibrary: () -> Unit,
    val onOpenDiscover: () -> Unit,
    val onOpenActivity: () -> Unit,
    val onOpenScreenshots: () -> Unit,
    /** All deals; Discover until a dedicated deals screen exists. */
    val onOpenDeals: () -> Unit,
    val onConnect: () -> Unit,
    /** Free-text "Спроси" search. */
    val onAsk: () -> Unit = {},
    /** This week's story recap. */
    val onOpenRecap: () -> Unit = {},
    val onOpenGiveaways: () -> Unit = {},
    val onOpenBadges: () -> Unit = {},
)

fun NavGraphBuilder.homeScreen(actions: HomeActions) {
    composable<HomeDestination> { HomeRoute(actions) }
}
