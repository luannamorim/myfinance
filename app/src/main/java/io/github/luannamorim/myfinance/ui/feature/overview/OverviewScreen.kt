package io.github.luannamorim.myfinance.ui.feature.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.common.OccurrenceRowItem
import io.github.luannamorim.myfinance.ui.common.TotalTiles
import io.github.luannamorim.myfinance.ui.common.suggestAssetAmount
import io.github.luannamorim.myfinance.ui.components.AdaptiveRow
import io.github.luannamorim.myfinance.ui.components.AmountDisplay
import io.github.luannamorim.myfinance.ui.components.AmountTone
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.FinanceChip
import io.github.luannamorim.myfinance.ui.components.IncompleteNote
import io.github.luannamorim.myfinance.ui.components.LegendDot
import io.github.luannamorim.myfinance.ui.components.NavRow
import io.github.luannamorim.myfinance.ui.components.RateFooter
import io.github.luannamorim.myfinance.ui.components.SectionHeader
import io.github.luannamorim.myfinance.ui.components.TabScreen
import io.github.luannamorim.myfinance.ui.components.TextAction
import io.github.luannamorim.myfinance.ui.components.shrinkToFit
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing
import java.time.LocalDate

data class OverviewActions(
    val onDisplay: (Currency) -> Unit = {},
    val onOpen: (Long) -> Unit = {},
    val onStartConfirm: (Long) -> Unit = {},
    val onDismissConfirm: () -> Unit = {},
    val onConfirm: (Money, LocalDate, String?, Money?) -> Unit = { _, _, _, _ -> },
    val onDismissError: () -> Unit = {},
    val onUpdateRates: () -> Unit = {},
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
            onUpdateRates = viewModel::updateRates,
        ),
    )
}

@Composable
fun OverviewContent(state: OverviewUiState, actions: OverviewActions, modifier: Modifier = Modifier) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    TabScreen(
        title = stringResource(R.string.nav_overview),
        modifier = modifier,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Currency.entries.forEach { cur -> FinanceChip(cur.symbol, selected = state.display == cur, onClick = { actions.onDisplay(cur) }) }
            }
        },
    ) {
        if (state.loading) return@TabScreen
        val nw = state.netWorth
        if (nw != null) NetWorthHero(nw.total, nw.cash, nw.investments, nw.personal)

        TotalTiles(
            stringResource(R.string.overview_available), state.available,
            stringResource(R.string.overview_projected, formats.dates.dayMonth(state.today.withDayOfMonth(state.today.lengthOfMonth()))), state.projected,
        )

        state.month?.takeIf { state.hasTransactions }?.let { m ->
            SectionHeader(stringResource(R.string.overview_this_month), action = { TextAction(stringResource(R.string.overview_see_all), onClick = actions.onSeeCashFlow) })
            FinanceCard(Modifier.fillMaxWidth()) {
                MonthLine(stringResource(R.string.cashflow_income), m.receivedIncome, m.expectedIncome)
                MonthLine(stringResource(R.string.cashflow_expenses), m.paidExpenses, m.expectedExpenses)
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
        if (state.hasAssets && state.hasOpening) {
            Text(stringResource(R.string.overview_composition_note), style = FinanceTheme.type.caption, color = c.textTertiary, modifier = Modifier.padding(top = Spacing.s))
        }
        if (state.usesConversion) RateFooter(state.rate, onUpdate = actions.onUpdateRates, updating = state.updatingRates)
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

@Composable
private fun NetWorthHero(total: Total, cash: Total, investments: Total, personal: Total) {
    val c = FinanceTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
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

@Composable
private fun MonthLine(label: String, done: Total, expected: Total) {
    val formats = LocalFormats.current
    AdaptiveRow(
        main = { Text(label, style = FinanceTheme.type.body, color = FinanceTheme.colors.textSecondary) },
        trailing = {
            Text(
                stringResource(R.string.overview_income_line, formats.money.format(done.money), formats.money.formatNumber(expected.money.minor, expected.money.currency)),
                style = FinanceTheme.type.numeric,
                color = FinanceTheme.colors.textPrimary,
                maxLines = 1,
                autoSize = shrinkToFit(FinanceTheme.type.numeric),
            )
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
    )
}
