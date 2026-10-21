package app.playlog.feature.journal

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object BadgesDestination

fun NavController.navigateToBadges() = navigate(BadgesDestination) { launchSingleTop = true }

fun NavGraphBuilder.badgesScreen(onBack: () -> Unit) {
    composable<BadgesDestination> { BadgesRoute(onBack = onBack) }
}
