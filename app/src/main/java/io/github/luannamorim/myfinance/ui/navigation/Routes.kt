package io.github.luannamorim.myfinance.ui.navigation

import androidx.annotation.Keep
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.FlowKind
import kotlinx.serialization.Serializable

// Top-level tabs
@Serializable data object OverviewRoute
@Serializable data object CashFlowRoute
@Serializable data object PortfolioRoute
@Serializable data object HistoryRoute
@Serializable data object SettingsRoute

// Cash flow
/** New transaction (both ids 0), a recurring template ([templateId]) or a standalone occurrence ([occurrenceId]). */
@Serializable data class TransactionEditorRoute(val templateId: Long = 0, val occurrenceId: Long = 0, val kind: EntryKind = EntryKind.EXPENSE)
@Serializable data class OccurrenceDetailRoute(val id: Long)
@Serializable data object RecurringListRoute
@Serializable data object CashFlowChartsRoute

// Categories
@Keep
enum class CategoryScope { INCOME, EXPENSE, ASSET }
@Serializable data class CategoriesRoute(val scope: CategoryScope = CategoryScope.EXPENSE)

// Portfolio
@Serializable data class AssetEditorRoute(val assetId: Long = 0)
@Serializable data class AssetDetailRoute(val assetId: Long)
@Serializable data class FlowEditorRoute(val assetId: Long, val kind: FlowKind)
@Serializable data class ValuationEditorRoute(val assetId: Long)
@Serializable data object PortfolioChartsRoute

// Settings
@Serializable data object OpeningBalancesRoute
@Serializable data object AdjustBalanceRoute
@Serializable data object CurrencySettingsRoute
@Serializable data object BackupRoute
@Serializable data object PrivacyRoute
@Serializable data object AboutRoute
