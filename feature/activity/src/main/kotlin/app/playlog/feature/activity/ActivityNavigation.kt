package app.playlog.feature.activity

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.playlog.core.model.GameId
import kotlinx.serialization.Serializable

@Serializable
data object ActivityDestination

fun NavGraphBuilder.activityScreen(onOpenGame: (GameId) -> Unit) {
    composable<ActivityDestination> { ActivityRoute(onOpenGame = onOpenGame) }
}
