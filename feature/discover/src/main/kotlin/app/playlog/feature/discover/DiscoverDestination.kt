package app.playlog.feature.discover

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.playlog.core.model.GameId
import kotlinx.serialization.Serializable

@Serializable
data object DiscoverDestination

fun NavGraphBuilder.discoverScreen(onOpenGame: (GameId) -> Unit) {
    composable<DiscoverDestination> { DiscoverRoute(onOpenGame = onOpenGame) }
}
