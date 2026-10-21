package app.playlog.feature.activity

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.playlog.core.model.ActivityPeriod
import app.playlog.core.model.GameId
import kotlinx.serialization.Serializable

@Serializable
data object ActivityDestination

fun NavGraphBuilder.activityScreen(onOpenGame: (GameId) -> Unit, onOpenRecap: (ActivityPeriod) -> Unit = {}) {
    composable<ActivityDestination> { ActivityRoute(onOpenGame = onOpenGame, onOpenRecap = onOpenRecap) }
}
