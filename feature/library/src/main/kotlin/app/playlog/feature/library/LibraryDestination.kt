package app.playlog.feature.library

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.playlog.core.model.GameId
import kotlinx.serialization.Serializable

@Serializable
data object LibraryDestination

fun NavGraphBuilder.libraryScreen(onOpenGame: (GameId) -> Unit, onConnect: () -> Unit) {
    composable<LibraryDestination> {
        LibraryRoute(onOpenGame = onOpenGame, onConnect = onConnect)
    }
}
