package app.playlog.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import app.playlog.core.designsystem.theme.PlaylogColors
import app.playlog.feature.activity.ActivityDestination
import app.playlog.feature.activity.activityScreen
import app.playlog.feature.discover.DiscoverDestination
import app.playlog.feature.discover.discoverScreen
import app.playlog.feature.deals.dealsScreen
import app.playlog.feature.deals.navigateToDeals
import app.playlog.feature.ask.askScreen
import app.playlog.feature.ask.navigateToAsk
import app.playlog.feature.game.gameScreen
import app.playlog.feature.game.navigateToGame
import app.playlog.feature.home.HomeActions
import app.playlog.feature.home.HomeDestination
import app.playlog.feature.home.homeScreen
import app.playlog.feature.library.LibraryDestination
import app.playlog.feature.library.libraryScreen
import app.playlog.feature.profile.ConnectDestination
import app.playlog.feature.profile.ProfileDestination
import app.playlog.feature.profile.connectScreen
import app.playlog.feature.profile.linkAccountScreen
import app.playlog.feature.profile.navigateToLink
import app.playlog.feature.profile.profileScreen
import app.playlog.feature.recap.navigateToRecap
import app.playlog.feature.recap.recapScreen
import app.playlog.feature.screenshots.ScreenshotsDestination
import app.playlog.feature.screenshots.navigateToScreenshots
import app.playlog.feature.screenshots.screenshotsScreen
import kotlin.reflect.KClass

/** Bottom tabs. Each has its own accent so the bar reads at a glance; screenshots live on Home and the game page. */
private enum class TopLevel(val route: Any, val routeClass: KClass<*>, val icon: ImageVector, val label: Int, val accent: Color) {
    HOME(HomeDestination, HomeDestination::class, Icons.Default.Home, R.string.tab_home, PlaylogColors.Lime),
    LIBRARY(LibraryDestination, LibraryDestination::class, Icons.Default.GridView, R.string.tab_library, PlaylogColors.Sky),
    DISCOVER(DiscoverDestination, DiscoverDestination::class, Icons.Default.AutoAwesome, R.string.tab_discover, PlaylogColors.Violet),
    ACTIVITY(ActivityDestination, ActivityDestination::class, Icons.Default.BarChart, R.string.tab_activity, PlaylogColors.Orange),
    PROFILE(ProfileDestination, ProfileDestination::class, Icons.Default.AccountCircle, R.string.tab_profile, PlaylogColors.Pink),
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
            startDestination = if (start == StartDestination.ONBOARDING) ConnectDestination else HomeDestination,
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
                navController.navigate(HomeDestination) {
                    popUpTo(ConnectDestination) { inclusive = true }
                }
            },
        )
        homeScreen(
            HomeActions(
                onOpenGame = navController::navigateToGame,
                onOpenLibrary = { navController.navigateToTab(TopLevel.LIBRARY) },
                onOpenDiscover = { navController.navigateToTab(TopLevel.DISCOVER) },
                onOpenActivity = { navController.navigateToTab(TopLevel.ACTIVITY) },
                onOpenScreenshots = { navController.navigateToScreenshots(null) },
                onOpenDeals = navController::navigateToDeals,
                onConnect = { navController.navigateToTab(TopLevel.PROFILE) },
                onAsk = { navController.navigateToAsk() },
                onOpenRecap = { navController.navigateToRecap() },
            ),
        )
        askScreen(onBack = { navController.popBackStack() }, onOpenGame = navController::navigateToGame)
        libraryScreen(onOpenGame = navController::navigateToGame, onConnect = { navController.navigateToTab(TopLevel.PROFILE) })
        discoverScreen(onOpenGame = navController::navigateToGame, onOpenAllDeals = navController::navigateToDeals)
        dealsScreen(onBack = { navController.popBackStack() })
        activityScreen(onOpenGame = navController::navigateToGame, onOpenRecap = { navController.navigateToRecap(it.name) })
        recapScreen(onClose = { navController.popBackStack() }, onOpenGame = navController::navigateToGame)
        screenshotsScreen(
            onOpenGame = navController::navigateToGame,
            onClearFilter = {
                navController.navigate(ScreenshotsDestination()) {
                    popUpTo<ScreenshotsDestination> { inclusive = true }
                }
            },
        )
        profileScreen(onLink = navController::navigateToLink)
        gameScreen(
            onBack = { navController.popBackStack() },
            onOpenAllScreenshots = { navController.navigateToScreenshots(it) },
            onFindSimilar = { title -> navController.navigateToAsk("как $title") },
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
                // One line, never wrapped: long Russian labels used to break onto a second line.
                label = {
                    Text(
                        stringResource(tab.label),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = tab.accent,
                    selectedIconColor = MaterialTheme.colorScheme.background,
                    selectedTextColor = tab.accent,
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
