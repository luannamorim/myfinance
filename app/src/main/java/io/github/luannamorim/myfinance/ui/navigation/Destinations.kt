package io.github.luannamorim.myfinance.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.luannamorim.myfinance.ui.feature.asset.AssetDetailScreen
import io.github.luannamorim.myfinance.ui.feature.asset.AssetEditorScreen
import io.github.luannamorim.myfinance.ui.feature.cashflow.CashFlowActions
import io.github.luannamorim.myfinance.ui.feature.cashflow.CashFlowChartsScreen
import io.github.luannamorim.myfinance.ui.feature.cashflow.CashFlowScreen
import io.github.luannamorim.myfinance.ui.feature.cashflow.RecurringListScreen
import io.github.luannamorim.myfinance.ui.feature.transaction.OccurrenceDetailScreen
import io.github.luannamorim.myfinance.ui.feature.transaction.TransactionEditorScreen
import io.github.luannamorim.myfinance.ui.feature.history.HistoryScreen
import io.github.luannamorim.myfinance.ui.feature.overview.OverviewScreen
import io.github.luannamorim.myfinance.ui.feature.portfolio.PortfolioActions
import io.github.luannamorim.myfinance.ui.feature.portfolio.PortfolioScreen
import io.github.luannamorim.myfinance.ui.feature.settings.AdjustBalanceScreen
import io.github.luannamorim.myfinance.ui.feature.settings.CategoriesScreen
import io.github.luannamorim.myfinance.ui.feature.settings.CurrencySettingsScreen
import io.github.luannamorim.myfinance.ui.feature.settings.OpeningBalancesScreen
import io.github.luannamorim.myfinance.ui.feature.settings.SettingsActions
import io.github.luannamorim.myfinance.ui.feature.settings.SettingsScreen

/** The five tab roots. */
internal fun NavGraphBuilder.tabDestinations(navController: NavHostController) {
    composable<OverviewRoute> { OverviewScreen() }
    composable<CashFlowRoute> {
        CashFlowScreen(
            CashFlowActions(
                onAdd = { navController.navigate(TransactionEditorRoute()) },
                onOpen = { navController.navigate(OccurrenceDetailRoute(it)) },
                onRecurring = { navController.navigate(RecurringListRoute) },
                onCharts = { navController.navigate(CashFlowChartsRoute) },
            ),
        )
    }
    composable<PortfolioRoute> {
        PortfolioScreen(
            PortfolioActions(
                onAdd = { navController.navigate(AssetEditorRoute()) },
                onOpen = { navController.navigate(AssetDetailRoute(it)) },
                onCharts = { navController.navigate(PortfolioChartsRoute) },
            ),
        )
    }
    composable<HistoryRoute> { HistoryScreen(onOpen = { navController.navigate(OccurrenceDetailRoute(it)) }) }
    composable<SettingsRoute> {
        SettingsScreen(
            SettingsActions(
                onOpenCategories = { navController.navigate(CategoriesRoute()) },
                onOpenOpening = { navController.navigate(OpeningBalancesRoute) },
                onOpenAdjust = { navController.navigate(AdjustBalanceRoute) },
                onOpenRecurring = { navController.navigate(RecurringListRoute) },
                onOpenCurrency = { navController.navigate(CurrencySettingsRoute) },
            ),
        )
    }
}

/** Settings sub-screens. */
internal fun NavGraphBuilder.settingsDestinations(navController: NavHostController) {
    composable<CategoriesRoute> { CategoriesScreen(onBack = { navController.popBackStack() }) }
    composable<CurrencySettingsRoute> { CurrencySettingsScreen(onBack = { navController.popBackStack() }) }
    composable<OpeningBalancesRoute> { OpeningBalancesScreen(onBack = { navController.popBackStack() }) }
    composable<AdjustBalanceRoute> { AdjustBalanceScreen(onBack = { navController.popBackStack() }) }
}

/** Transactions: editor, recurring list. */
internal fun NavGraphBuilder.cashDestinations(navController: NavHostController) {
    composable<TransactionEditorRoute> { TransactionEditorScreen(onClose = { navController.popBackStack() }) }
    composable<OccurrenceDetailRoute> {
        OccurrenceDetailScreen(
            onBack = { navController.popBackStack() },
            onEdit = { navController.navigate(TransactionEditorRoute(occurrenceId = it)) },
            onEditRecurring = { navController.navigate(TransactionEditorRoute(templateId = it)) },
        )
    }
    composable<CashFlowChartsRoute> { CashFlowChartsScreen(onBack = { navController.popBackStack() }) }
    composable<RecurringListRoute> {
        RecurringListScreen(
            onBack = { navController.popBackStack() },
            onOpen = { navController.navigate(TransactionEditorRoute(templateId = it)) },
            onAdd = { navController.navigate(TransactionEditorRoute()) },
        )
    }
}

/** Portfolio: asset editor and detail. */
internal fun NavGraphBuilder.portfolioDestinations(navController: NavHostController) {
    composable<AssetEditorRoute> { AssetEditorScreen(onClose = { navController.popBackStack() }) }
    composable<AssetDetailRoute> {
        AssetDetailScreen(
            onBack = { navController.popBackStack() },
            onEdit = { navController.navigate(AssetEditorRoute(assetId = it)) },
        )
    }
}
