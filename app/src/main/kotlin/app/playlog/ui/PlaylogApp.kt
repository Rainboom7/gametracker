package app.playlog.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.playlog.MainViewModel
import app.playlog.R
import app.playlog.StartDestination
import app.playlog.feature.activity.ActivityDestination
import app.playlog.feature.activity.activityScreen
import app.playlog.feature.game.gameScreen
import app.playlog.feature.game.navigateToGame
import app.playlog.feature.library.LibraryDestination
import app.playlog.feature.library.libraryScreen
import app.playlog.feature.profile.ConnectDestination
import app.playlog.feature.profile.ProfileDestination
import app.playlog.feature.profile.connectScreen
import app.playlog.feature.profile.linkAccountScreen
import app.playlog.feature.profile.navigateToLink
import app.playlog.feature.profile.profileScreen
import app.playlog.feature.screenshots.ScreenshotsDestination
import app.playlog.feature.screenshots.navigateToScreenshots
import app.playlog.feature.screenshots.screenshotsScreen
import kotlin.reflect.KClass

private enum class TopLevel(val route: Any, val routeClass: KClass<*>, val icon: ImageVector, val label: Int) {
    LIBRARY(LibraryDestination, LibraryDestination::class, Icons.Default.GridView, R.string.tab_library),
    ACTIVITY(ActivityDestination, ActivityDestination::class, Icons.Default.BarChart, R.string.tab_activity),
    SCREENSHOTS(ScreenshotsDestination(), ScreenshotsDestination::class, Icons.Default.PhotoLibrary, R.string.tab_screenshots),
    PROFILE(ProfileDestination, ProfileDestination::class, Icons.Default.AccountCircle, R.string.tab_profile),
}

@Composable
fun PlaylogApp(mainViewModel: MainViewModel = hiltViewModel()) {
    val start by mainViewModel.start.collectAsStateWithLifecycle()
    if (start == StartDestination.LOADING) {
        Box(Modifier.fillMaxSize())
        return
    }
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val currentTab = TopLevel.entries.firstOrNull { tab -> destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { if (currentTab != null) BottomBar(currentTab) { navController.navigateToTab(it) } },
    ) { padding ->
        PlaylogNavHost(
            navController = navController,
            startDestination = if (start == StartDestination.ONBOARDING) ConnectDestination else LibraryDestination,
            contentPadding = padding,
        )
    }
}

@Composable
private fun PlaylogNavHost(navController: NavHostController, startDestination: Any, contentPadding: PaddingValues) {
    NavHost(navController = navController, startDestination = startDestination, modifier = Modifier.padding(contentPadding)) {
        connectScreen(
            onLink = navController::navigateToLink,
            onFinished = {
                navController.navigate(LibraryDestination) {
                    popUpTo(ConnectDestination) { inclusive = true }
                }
            },
        )
        libraryScreen(onOpenGame = navController::navigateToGame, onConnect = { navController.navigateToTab(TopLevel.PROFILE) })
        activityScreen(onOpenGame = navController::navigateToGame)
        screenshotsScreen(
            onOpenGame = navController::navigateToGame,
            onClearFilter = { navController.navigateToTab(TopLevel.SCREENSHOTS) },
        )
        profileScreen(onLink = navController::navigateToLink)
        gameScreen(
            onBack = { navController.popBackStack() },
            onOpenAllScreenshots = { navController.navigateToScreenshots(it) },
        )
        linkAccountScreen(onDone = { navController.popBackStack() })
    }
}

@Composable
private fun BottomBar(current: TopLevel, onSelect: (TopLevel) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        TopLevel.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun NavHostController.navigateToTab(tab: TopLevel) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
