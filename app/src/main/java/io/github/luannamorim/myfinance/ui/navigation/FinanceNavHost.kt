package io.github.luannamorim.myfinance.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme

@Composable
fun FinanceNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    /** Incremented by MainActivity when the backup failure notification is tapped. */
    openBackupRequest: Int = 0,
) {
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val currentTop = TopLevelDestination.entries.firstOrNull { destination.isOn(it) }
    val c = FinanceTheme.colors

    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = c.textPrimary,
            selectedTextColor = c.textPrimary,
            indicatorColor = c.surfaceRaised,
            unselectedIconColor = c.textTertiary,
            unselectedTextColor = c.textTertiary,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = c.textPrimary,
            selectedTextColor = c.textPrimary,
            indicatorColor = c.surfaceRaised,
            unselectedIconColor = c.textTertiary,
            unselectedTextColor = c.textTertiary,
        ),
    )
    // Editors and detail screens are full screen: no bottom bar or rail.
    val layoutType = if (currentTop == null && destination != null) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
    }

    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = layoutType,
        containerColor = c.background,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = c.background,
            navigationRailContainerColor = c.background,
            navigationBarContentColor = c.textSecondary,
            navigationRailContentColor = c.textSecondary,
        ),
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { top ->
                val selected = top == currentTop
                item(
                    selected = selected,
                    onClick = { navController.navigateToTopLevel(top) },
                    icon = { Icon(if (selected) top.selectedIcon else top.icon, contentDescription = null) },
                    label = { Text(stringResource(top.labelRes), style = FinanceTheme.type.caption, maxLines = 1) },
                    colors = itemColors,
                )
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = OverviewRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(TRANSITION_MS)) },
            exitTransition = { fadeOut(tween(TRANSITION_MS)) },
            popEnterTransition = { fadeIn(tween(TRANSITION_MS)) },
            popExitTransition = { fadeOut(tween(TRANSITION_MS)) },
        ) {
            financeGraph(navController)
        }
        // After NavHost so the graph is set when a notification asks for the backup screen.
        LaunchedEffect(openBackupRequest) {
            if (openBackupRequest > 0) navController.navigate(BackupRoute) { launchSingleTop = true }
        }
    }
}

private const val TRANSITION_MS = 200

private fun NavDestination?.isOn(top: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(top.routeClass) } == true

fun NavHostController.navigateToTopLevel(top: TopLevelDestination) {
    navigate(top.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Every destination of the app. Kept apart from the scaffold so features add their screens here. */
internal fun NavGraphBuilder.financeGraph(navController: NavHostController) {
    tabDestinations(navController)
    settingsDestinations(navController)
    cashDestinations(navController)
    portfolioDestinations(navController)
}
