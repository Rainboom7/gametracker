package app.playlog.feature.discover

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.playlog.core.model.GameId
import kotlinx.serialization.Serializable

@Serializable
data object DiscoverDestination

@Serializable
data object RouletteDestination

fun NavController.navigateToRoulette() = navigate(RouletteDestination) { launchSingleTop = true }

fun NavGraphBuilder.rouletteScreen(onBack: () -> Unit, onOpenGame: (GameId) -> Unit) {
    composable<RouletteDestination> { RouletteRoute(onBack = onBack, onOpenGame = onOpenGame) }
}

fun NavGraphBuilder.discoverScreen(
    onOpenGame: (GameId) -> Unit,
    onOpenAllDeals: () -> Unit,
    onOpenGiveaways: () -> Unit = {},
    onOpenBadges: () -> Unit = {},
    onOpenReleases: () -> Unit = {},
) {
    composable<DiscoverDestination> {
        DiscoverRoute(
            onOpenGame = onOpenGame,
            onOpenAllDeals = onOpenAllDeals,
            onOpenGiveaways = onOpenGiveaways,
            onOpenBadges = onOpenBadges,
            onOpenReleases = onOpenReleases,
        )
    }
}
