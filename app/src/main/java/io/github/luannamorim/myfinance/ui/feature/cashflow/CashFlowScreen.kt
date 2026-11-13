package io.github.luannamorim.myfinance.ui.feature.cashflow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.common.OccurrenceRow
import io.github.luannamorim.myfinance.ui.common.OccurrenceRowItem
import io.github.luannamorim.myfinance.ui.common.TotalTiles
import io.github.luannamorim.myfinance.ui.components.AdaptiveRow
import io.github.luannamorim.myfinance.ui.components.AmountDisplay
import io.github.luannamorim.myfinance.ui.components.AmountTone
import io.github.luannamorim.myfinance.ui.components.EmptyState
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.IncompleteNote
import io.github.luannamorim.myfinance.ui.components.MonthSwitcher
import io.github.luannamorim.myfinance.ui.components.NavRow
import io.github.luannamorim.myfinance.ui.components.PrimaryButton
import io.github.luannamorim.myfinance.ui.components.RateFooter
import io.github.luannamorim.myfinance.ui.components.SectionHeader
import io.github.luannamorim.myfinance.ui.components.TabScreen
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing
import java.time.YearMonth

data class CashFlowActions(
    val onMonth: (YearMonth) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onOpen: (Long) -> Unit = {},
    val onRecurring: () -> Unit = {},
    val onCharts: () -> Unit = {},
)

@Composable
fun CashFlowScreen(actions: CashFlowActions, viewModel: CashFlowViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CashFlowContent(state, actions.copy(onMonth = viewModel::setMonth))
}

@Composable
fun CashFlowContent(state: CashFlowUiState, actions: CashFlowActions, modifier: Modifier = Modifier) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    TabScreen(
        title = stringResource(R.string.nav_cash_flow),
        modifier = modifier,
        actions = { IconButton(onClick = actions.onAdd) { Icon(Icons.Rounded.Add, stringResource(R.string.cashflow_add), tint = c.textPrimary) } },
    ) {
        if (state.loading) return@TabScreen
        if (state.empty) {
            EmptyState(
                stringResource(R.string.cashflow_empty_title),
                stringResource(R.string.cashflow_empty_message),
                action = { PrimaryButton(stringResource(R.string.cashflow_add), onClick = actions.onAdd) },
            )
            return@TabScreen
        }
        MonthSwitcher(state.month, actions.onMonth)
        TotalTiles(
            if (state.balanceIsToday) stringResource(R.string.cashflow_available) else stringResource(R.string.cashflow_balance_end, formats.dates.dayMonth(state.month.atEndOfMonth())),
            state.balance,
            stringResource(R.string.cashflow_projected, formats.dates.dayMonth(state.month.atEndOfMonth())),
            state.projectedEnd,
        )
        state.summary?.let { s ->
            ProgressCard(stringResource(R.string.cashflow_income), s.receivedIncome, s.expectedIncome, stringResource(R.string.cashflow_received_of, formats.money.format(s.expectedIncome.money)), c.positive)
            ProgressCard(stringResource(R.string.cashflow_expenses), s.paidExpenses, s.expectedExpenses, stringResource(R.string.cashflow_paid_of, formats.money.format(s.expectedExpenses.money)), c.negative)
            if (s.overdueEarlierCount > 0 && state.month != YearMonth.from(state.today)) {
                Text(
                    pluralStringResource(R.plurals.cashflow_overdue_earlier, s.overdueEarlierCount, s.overdueEarlierCount, formats.money.format(s.overdueEarlier.money)),
                    style = FinanceTheme.type.caption,
                    color = c.danger,
                )
            }
        }
        Section(stringResource(R.string.cashflow_overdue), state.overdue, actions.onOpen)
        Section(stringResource(R.string.cashflow_pending_bills), state.pendingBills, actions.onOpen)
        Section(stringResource(R.string.cashflow_upcoming_income), state.upcomingIncome, actions.onOpen)
        if (state.overdue.isEmpty() && state.pendingBills.isEmpty() && state.upcomingIncome.isEmpty()) {
            Text(stringResource(R.string.cashflow_nothing_pending), style = FinanceTheme.type.body, color = c.textSecondary)
        }
        state.nextMonth?.let { next ->
            SectionHeader(stringResource(R.string.cashflow_next_month), Modifier.padding(top = Spacing.s))
            FinanceCard(Modifier.fillMaxWidth()) {
                SummaryLine(stringResource(R.string.cashflow_income), next.expectedIncome, AmountTone.NEUTRAL)
                SummaryLine(stringResource(R.string.cashflow_expenses), Total(-next.expectedExpenses.money, next.expectedExpenses.incomplete), AmountTone.NEUTRAL)
                Spacer(Modifier.height(Spacing.s))
                state.nextMonthProjected?.let {
                    SummaryLine(stringResource(R.string.cashflow_next_projected, formats.dates.monthYear(state.month.plusMonths(1))), it, AmountTone.AUTO)
                }
            }
        }
        NavRow(Icons.Rounded.Repeat, stringResource(R.string.recurring_title), stringResource(R.string.recurring_hint), actions.onRecurring, Modifier.padding(top = Spacing.s))
        NavRow(Icons.AutoMirrored.Rounded.ShowChart, stringResource(R.string.cashflow_charts), stringResource(R.string.cashflow_charts_hint), actions.onCharts)
        if (state.usesConversion) RateFooter(state.rate)
    }
}

@Composable
private fun ProgressCard(title: String, done: Total, expected: Total, caption: String, color: Color) {
    val c = FinanceTheme.colors
    FinanceCard(Modifier.fillMaxWidth()) {
        AdaptiveRow(
            main = { Text(title, style = FinanceTheme.type.bodyStrong, color = c.textPrimary) },
            trailing = { AmountDisplay(done.money, approximate = done.incomplete) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(caption, style = FinanceTheme.type.caption, color = c.textSecondary)
        Spacer(Modifier.height(Spacing.s))
        val share = if (expected.money.minor > 0) (done.money.minor.toFloat() / expected.money.minor).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(6.dp).background(c.surfaceRaised, CircleShape)) {
            if (share > 0f) Box(Modifier.fillMaxWidth(share).height(6.dp).background(color, CircleShape))
        }
        if (done.incomplete || expected.incomplete) IncompleteNote(Modifier.padding(top = Spacing.s))
    }
}

@Composable
private fun SummaryLine(label: String, total: Total, tone: AmountTone) {
    AdaptiveRow(
        main = { Text(label, style = FinanceTheme.type.body, color = FinanceTheme.colors.textSecondary) },
        trailing = { AmountDisplay(total.money, tone = tone, approximate = total.incomplete) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Section(title: String, rows: List<OccurrenceRow>, onOpen: (Long) -> Unit) {
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(top = Spacing.s)) {
        SectionHeader(title)
        rows.forEach { row -> OccurrenceRowItem(row, onClick = { onOpen(row.id) }) }
    }
}
