package io.github.luannamorim.myfinance.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.luannamorim.myfinance.ui.feature.cashflow.CashFlowScreen
import io.github.luannamorim.myfinance.ui.feature.history.HistoryScreen
import io.github.luannamorim.myfinance.ui.feature.overview.OverviewScreen
import io.github.luannamorim.myfinance.ui.feature.portfolio.PortfolioScreen
import io.github.luannamorim.myfinance.ui.feature.settings.CategoriesScreen
import io.github.luannamorim.myfinance.ui.feature.settings.SettingsActions
import io.github.luannamorim.myfinance.ui.feature.settings.SettingsScreen

/** The five tab roots. */
internal fun NavGraphBuilder.tabDestinations(navController: NavHostController) {
    composable<OverviewRoute> { OverviewScreen() }
    composable<CashFlowRoute> { CashFlowScreen() }
    composable<PortfolioRoute> { PortfolioScreen() }
    composable<HistoryRoute> { HistoryScreen() }
    composable<SettingsRoute> {
        SettingsScreen(SettingsActions(onOpenCategories = { navController.navigate(CategoriesRoute()) }))
    }
}

/** Settings sub-screens. */
internal fun NavGraphBuilder.settingsDestinations(navController: NavHostController) {
    composable<CategoriesRoute> { CategoriesScreen(onBack = { navController.popBackStack() }) }
}
