package app.playlog.feature.deals

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object DealsDestination

fun NavController.navigateToDeals() = navigate(DealsDestination) { launchSingleTop = true }

fun NavGraphBuilder.dealsScreen(onBack: () -> Unit) {
    composable<DealsDestination> { DealsRoute(onBack = onBack) }
}
