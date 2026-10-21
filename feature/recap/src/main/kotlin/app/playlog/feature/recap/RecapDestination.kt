package app.playlog.feature.recap

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.playlog.core.model.ActivityPeriod
import app.playlog.core.model.GameId
import kotlinx.serialization.Serializable

/** [period] is an [ActivityPeriod] name: "WEEK", "MONTH" or "YEAR". */
@Serializable
data class RecapDestination(val period: String = ActivityPeriod.WEEK.name)

fun NavController.navigateToRecap(period: String = ActivityPeriod.WEEK.name) = navigate(RecapDestination(period))

fun NavGraphBuilder.recapScreen(onClose: () -> Unit, onOpenGame: (GameId) -> Unit) {
    composable<RecapDestination> { RecapRoute(onClose = onClose, onOpenGame = onOpenGame) }
}
