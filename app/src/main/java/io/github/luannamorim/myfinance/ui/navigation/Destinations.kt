package io.github.luannamorim.myfinance.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.luannamorim.myfinance.ui.feature.cashflow.CashFlowScreen
import io.github.luannamorim.myfinance.ui.feature.cashflow.RecurringListScreen
import io.github.luannamorim.myfinance.ui.feature.transaction.OccurrenceDetailScreen
import io.github.luannamorim.myfinance.ui.feature.transaction.TransactionEditorScreen
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
    composable<RecurringListRoute> {
        RecurringListScreen(
            onBack = { navController.popBackStack() },
            onOpen = { navController.navigate(TransactionEditorRoute(templateId = it)) },
            onAdd = { navController.navigate(TransactionEditorRoute()) },
        )
    }
}
