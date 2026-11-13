package io.github.luannamorim.myfinance.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.luannamorim.myfinance.ui.feature.cashflow.CashFlowScreen
import io.github.luannamorim.myfinance.ui.feature.history.HistoryScreen
import io.github.luannamorim.myfinance.ui.feature.overview.OverviewScreen
import io.github.luannamorim.myfinance.ui.feature.portfolio.PortfolioScreen
import io.github.luannamorim.myfinance.ui.feature.settings.SettingsScreen

/** The five tab roots. */
internal fun NavGraphBuilder.tabDestinations(@Suppress("UNUSED_PARAMETER") navController: NavHostController) {
    composable<OverviewRoute> { OverviewScreen() }
    composable<CashFlowRoute> { CashFlowScreen() }
    composable<PortfolioRoute> { PortfolioScreen() }
    composable<HistoryRoute> { HistoryScreen() }
    composable<SettingsRoute> { SettingsScreen() }
}
