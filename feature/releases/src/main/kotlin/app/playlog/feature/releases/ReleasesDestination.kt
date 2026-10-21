package app.playlog.feature.releases

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ReleasesDestination

fun NavController.navigateToReleases() = navigate(ReleasesDestination) { launchSingleTop = true }

fun NavGraphBuilder.releasesScreen(onBack: () -> Unit) {
    composable<ReleasesDestination> { ReleasesRoute(onBack = onBack) }
}
