package io.github.luannamorim.myfinance.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.luannamorim.myfinance.R
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    OVERVIEW(OverviewRoute, OverviewRoute::class, R.string.nav_overview, Icons.Outlined.SpaceDashboard, Icons.Rounded.SpaceDashboard),
    CASH_FLOW(CashFlowRoute, CashFlowRoute::class, R.string.nav_cash_flow, Icons.Outlined.SwapVert, Icons.Rounded.SwapVert),
    PORTFOLIO(PortfolioRoute, PortfolioRoute::class, R.string.nav_portfolio, Icons.Outlined.DonutLarge, Icons.Rounded.DonutLarge),
    HISTORY(HistoryRoute, HistoryRoute::class, R.string.nav_history, Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Rounded.ReceiptLong),
    SETTINGS(SettingsRoute, SettingsRoute::class, R.string.nav_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
}
