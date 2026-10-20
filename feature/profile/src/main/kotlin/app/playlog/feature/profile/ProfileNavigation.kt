package app.playlog.feature.profile

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.playlog.core.model.Platform
import kotlinx.serialization.Serializable

@Serializable
data object ProfileDestination

@Serializable
data object ConnectDestination

@Serializable
data class LinkAccountDestination(val platform: String)

fun NavController.navigateToLink(platform: Platform) = navigate(LinkAccountDestination(platform.name))

fun NavGraphBuilder.profileScreen(onLink: (Platform) -> Unit) {
    composable<ProfileDestination> { ProfileRoute(onLink = onLink) }
}

fun NavGraphBuilder.connectScreen(onLink: (Platform) -> Unit, onFinished: () -> Unit) {
    composable<ConnectDestination> { ConnectRoute(onLink = onLink, onFinished = onFinished) }
}

fun NavGraphBuilder.linkAccountScreen(onDone: () -> Unit) {
    composable<LinkAccountDestination> { LinkAccountRoute(onDone = onDone) }
}
