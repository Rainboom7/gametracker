package app.playlog.feature.deals

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object DealsDestination

fun NavController.navigateToDeals() = navigate(DealsDestination) { launchSingleTop = true }

@Serializable
data object GiveawaysDestination

fun NavController.navigateToGiveaways() = navigate(GiveawaysDestination) { launchSingleTop = true }

/** Registers both the deals screen and the giveaways screen it links to. */
fun NavGraphBuilder.dealsScreen(onBack: () -> Unit, onOpenGiveaways: () -> Unit = {}) {
    composable<DealsDestination> { DealsRoute(onBack = onBack, onOpenGiveaways = onOpenGiveaways) }
    composable<GiveawaysDestination> { GiveawaysRoute(onBack = onBack) }
}
