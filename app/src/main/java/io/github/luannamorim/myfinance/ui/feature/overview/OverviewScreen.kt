package io.github.luannamorim.myfinance.ui.feature.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.ui.common.ActionErrorDialog
import io.github.luannamorim.myfinance.ui.common.ConfirmSheet
import io.github.luannamorim.myfinance.ui.common.IconLabel
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.common.OccurrenceRowItem
import io.github.luannamorim.myfinance.ui.common.SummaryIcons
import io.github.luannamorim.myfinance.ui.common.TotalTiles
import io.github.luannamorim.myfinance.ui.common.suggestAssetAmount
import io.github.luannamorim.myfinance.ui.components.AdaptiveRow
import io.github.luannamorim.myfinance.ui.components.AmountDisplay
import io.github.luannamorim.myfinance.ui.components.AmountTone
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.IncompleteNote
import io.github.luannamorim.myfinance.ui.components.LegendDot
import io.github.luannamorim.myfinance.ui.components.NavRow
import io.github.luannamorim.myfinance.ui.components.SectionHeader
import io.github.luannamorim.myfinance.ui.components.TabScreen
import io.github.luannamorim.myfinance.ui.components.TextAction
import io.github.luannamorim.myfinance.ui.theme.FinanceShapes
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing
import java.time.LocalDate
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import io.github.luannamorim.myfinance.ui.common.displayName
import io.github.luannamorim.myfinance.ui.components.CurrencyFlag

data class OverviewActions(
    val onDisplay: (Currency) -> Unit = {},
    val onOpen: (Long) -> Unit = {},
    val onStartConfirm: (Long) -> Unit = {},
    val onDismissConfirm: () -> Unit = {},
    val onConfirm: (Money, LocalDate, String?, Money?) -> Unit = { _, _, _, _ -> },
    val onDismissError: () -> Unit = {},
    val onSeeCashFlow: () -> Unit = {},
    val onSeeHistory: () -> Unit = {},
    val onOpening: () -> Unit = {},
    val onAddTransaction: () -> Unit = {},
    val onAddAsset: () -> Unit = {},
)

@Composable
fun OverviewScreen(actions: OverviewActions, viewModel: OverviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OverviewContent(
        state,
        actions.copy(
            onDisplay = viewModel::setDisplay,
            onStartConfirm = viewModel::startConfirm,
            onDismissConfirm = viewModel::dismissConfirm,
            onConfirm = viewModel::confirm,
            onDismissError = viewModel::dismissError,
        ),
    )
}

@Composable
fun OverviewContent(state: OverviewUiState, actions: OverviewActions, modifier: Modifier = Modifier) {
    val formats = LocalFormats.current
    TabScreen(
        title = stringResource(R.string.nav_overview),
        modifier = modifier,
        subtitle = formats.dates.longDate(state.today).takeUnless { state.loading },
        actions = {
            AddMenu(actions.onAddTransaction, actions.onAddAsset)
            if (!state.loading) FlagButton(state.display, state.nextDisplay, actions.onDisplay)
        },
    ) {
        if (state.loading) return@TabScreen
        val nw = state.netWorth
        if (nw != null) NetWorthHero(nw.total, nw.cash, nw.investments, nw.personal)

        TotalTiles(
            stringResource(R.string.overview_available), SummaryIcons.Cash, state.available,
            stringResource(R.string.overview_projected, formats.dates.dayMonth(state.today.withDayOfMonth(state.today.lengthOfMonth()))), SummaryIcons.Projected, state.projected,
        )

        state.month?.takeIf { state.hasTransactions }?.let { m ->
            SectionHeader(stringResource(R.string.overview_this_month), action = { TextAction(stringResource(R.string.overview_see_all), onClick = actions.onSeeCashFlow) })
            FinanceCard(Modifier.fillMaxWidth()) {
                // What the month expects; what is done so far lives in Cash Flow.
                MonthLine(stringResource(R.string.cashflow_income), SummaryIcons.Income, m.expectedIncome)
                MonthLine(stringResource(R.string.cashflow_expenses), SummaryIcons.Expenses, m.expectedExpenses)
                Spacer(Modifier.height(Spacing.s))
                MonthLine(stringResource(R.string.cashflow_result), SummaryIcons.Result, m.expectedIncome - m.expectedExpenses, AmountTone.AUTO)
            }
        }

        if (state.upcoming.isNotEmpty()) {
            SectionHeader(stringResource(R.string.overview_upcoming), Modifier.padding(top = Spacing.s), action = { TextAction(stringResource(R.string.overview_see_all), onClick = actions.onSeeCashFlow) })
            state.upcoming.forEach { row ->
                OccurrenceRowItem(row, onClick = { actions.onOpen(row.id) }, trailing = {
                    TextAction(stringResource(R.string.overview_confirm), onClick = { actions.onStartConfirm(row.id) })
                })
            }
        }
        if (state.recent.isNotEmpty()) {
            SectionHeader(stringResource(R.string.overview_recent), Modifier.padding(top = Spacing.s), action = { TextAction(stringResource(R.string.overview_see_all), onClick = actions.onSeeHistory) })
            state.recent.forEach { row -> OccurrenceRowItem(row, onClick = { actions.onOpen(row.id) }) }
        }

        if (!state.hasOpening || !state.hasTransactions || !state.hasAssets) {
            SectionHeader(stringResource(R.string.overview_start_title), Modifier.padding(top = Spacing.s))
            if (!state.hasOpening) NavRow(Icons.Rounded.AccountBalanceWallet, stringResource(R.string.overview_start_opening), stringResource(R.string.overview_start_opening_hint), actions.onOpening)
            if (!state.hasTransactions) NavRow(Icons.Rounded.Add, stringResource(R.string.overview_start_transaction), stringResource(R.string.overview_start_transaction_hint), actions.onAddTransaction)
            if (!state.hasAssets) NavRow(Icons.Rounded.DonutLarge, stringResource(R.string.overview_start_asset), stringResource(R.string.overview_start_asset_hint), actions.onAddAsset)
        }
    }
    state.confirming?.let { row ->
        ConfirmSheet(
            row.occurrence, state.today, actions.onConfirm, actions.onDismissConfirm,
            target = row.occurrence.assetId?.let(state.targets::get),
            suggest = { money, currency, date -> state.rates.suggestAssetAmount(money, currency, date) },
        )
    }
    ActionErrorDialog(state.error, actions.onDismissError)
}

/** A quiet "+" in the header: a transaction (Cash Flow) or an asset (Portfolio), with the icons of their tabs. */
@Composable
private fun AddMenu(onAddTransaction: () -> Unit, onAddAsset: () -> Unit) {
    val c = FinanceTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.overview_add), tint = c.textSecondary) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = FinanceShapes.medium, containerColor = c.surfaceRaised) {
            AddMenuItem(Icons.Rounded.SwapVert, stringResource(R.string.cashflow_add)) { open = false; onAddTransaction() }
            AddMenuItem(Icons.Rounded.DonutLarge, stringResource(R.string.portfolio_add)) { open = false; onAddAsset() }
        }
    }
}

/**
 * The flag of the currency amounts are shown in; a tap switches to [next] (the next one turned on in
 * Settings). With a single currency on, [next] is null and the flag only tells which one it is.
 */
@Composable
private fun FlagButton(display: Currency, next: Currency?, onSwitch: (Currency) -> Unit) {
    val description = if (next != null) {
        stringResource(R.string.overview_display_switch, display.displayName(), next.displayName())
    } else {
        stringResource(R.string.overview_display_currency, display.displayName())
    }
    val tap = if (next != null) Modifier.clickable(role = Role.Button) { onSwitch(next) } else Modifier
    Box(
        Modifier.size(48.dp).clip(CircleShape).then(tap).semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(display, animationSpec = tween(200), label = "flag") { CurrencyFlag(it, 32.dp) }
    }
}

@Composable
private fun AddMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = FinanceTheme.colors
    DropdownMenuItem(
        text = { Text(label, style = FinanceTheme.type.body, color = c.textPrimary) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = c.textSecondary) },
        onClick = onClick,
    )
}

@Composable
private fun NetWorthHero(total: Total, cash: Total, investments: Total, personal: Total) {
    val c = FinanceTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(stringResource(R.string.overview_net_worth).uppercase(), style = FinanceTheme.type.overline, color = c.textSecondary, modifier = Modifier.semantics { heading() })
        AmountDisplay(total.money, style = FinanceTheme.type.numericHero, tone = if (total.money.isNegative) AmountTone.NEGATIVE else AmountTone.NEUTRAL, approximate = total.incomplete)
        val parts = listOf(
            Triple(cash, c.chart[0], stringResource(R.string.overview_cash)),
            Triple(investments, c.accent, stringResource(R.string.overview_investments)),
            Triple(personal, c.chart[3], stringResource(R.string.overview_personal)),
        )
        CompositionBar(parts.map { it.first.money.minor.coerceAtLeast(0).toFloat() to it.second })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            parts.forEach { (value, color, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(color, label)
                    Text(" " + LocalFormats.current.money.format(value.money), style = FinanceTheme.type.caption.copy(fontFeatureSettings = "tnum"), color = c.textPrimary)
                }
            }
        }
        if (total.incomplete) IncompleteNote()
    }
}

@Composable
private fun CompositionBar(parts: List<Pair<Float, Color>>) {
    val c = FinanceTheme.colors
    val sum = parts.sumOf { it.first.toDouble() }.toFloat()
    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.surfaceRaised), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (sum > 0f) {
            parts.filter { it.first > 0f }.forEach { (value, color) ->
                Box(Modifier.weight(value / sum).height(8.dp).background(color))
            }
        }
    }
}

/** A "this month" line: what the month expects, signed and coloured for the P&L ([tone] AUTO). */
@Composable
private fun MonthLine(label: String, icon: ImageVector, total: Total, tone: AmountTone = AmountTone.NEUTRAL) {
    AdaptiveRow(
        main = { IconLabel(icon, 18.dp) { Text(label, style = FinanceTheme.type.body, color = FinanceTheme.colors.textSecondary) } },
        trailing = { AmountDisplay(total.money, signed = tone == AmountTone.AUTO, tone = tone, approximate = total.incomplete) },
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
    )
}
