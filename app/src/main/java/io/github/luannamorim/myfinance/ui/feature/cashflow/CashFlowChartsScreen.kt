package io.github.luannamorim.myfinance.ui.feature.cashflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.domain.cash.CashCalculator
import io.github.luannamorim.myfinance.domain.cash.CashLedger
import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.domain.model.Category
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.common.displayName
import io.github.luannamorim.myfinance.ui.components.BarItem
import io.github.luannamorim.myfinance.ui.components.CategoryBars
import io.github.luannamorim.myfinance.ui.components.DetailScaffold
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.FinanceColumnChart
import io.github.luannamorim.myfinance.ui.components.FinanceLineChart
import io.github.luannamorim.myfinance.ui.components.LegendDot
import io.github.luannamorim.myfinance.ui.components.LineSeries
import io.github.luannamorim.myfinance.ui.components.MonthSwitcher
import io.github.luannamorim.myfinance.ui.components.SectionHeader
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

data class CategoryTotal(val category: Category?, val total: Total)

data class CashFlowChartsUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val display: Currency = Currency.BRL,
    /** Last six months ending at [month]: (month, income, expenses). */
    val incomeVsExpenses: List<Triple<YearMonth, Money, Money>> = emptyList(),
    /** Month-end balances from the first month with data (at most 12). */
    val balance: List<Pair<YearMonth, Money>> = emptyList(),
    val expensesByCategory: List<CategoryTotal> = emptyList(),
    val incomeByCategory: List<CategoryTotal> = emptyList(),
)

private const val TOP_CATEGORIES = 6

/** Chart data from the ledger; every number comes from CashCalculator. */
fun cashFlowCharts(ledger: CashLedger, categories: List<Category>, rates: ExchangeRates, display: Currency, month: YearMonth, today: java.time.LocalDate): CashFlowChartsUiState {
    val converter = Converter(rates)
    val byId = categories.associateBy { it.id }
    val sixMonths = (5 downTo 0).map { month.minusMonths(it.toLong()) }
    val bars = sixMonths.map { m ->
        val s = CashCalculator.monthSummary(ledger, m, today, display, converter)
        Triple(m, s.expectedIncome.money, s.expectedExpenses.money)
    }
    val firstDate = (ledger.occurrences.mapNotNull { it.actualDate } + ledger.adjustments.map { it.date }).minOrNull()
    val current = YearMonth.from(today)
    val balance = if (firstDate == null) {
        emptyList()
    } else {
        val start = maxOf(YearMonth.from(firstDate), current.minusMonths(11))
        val months = generateSequence(start) { it.plusMonths(1) }.takeWhile { !it.isAfter(current) }.toList()
        CashCalculator.balanceHistory(ledger, months, display, converter).map { it.first to it.second.money }
    }
    fun top(kind: EntryKind) = CashCalculator.byCategory(ledger, month, kind, display, converter).let { list ->
        val head = list.take(TOP_CATEGORIES).map { CategoryTotal(byId[it.first], it.second) }
        val rest = list.drop(TOP_CATEGORIES)
        if (rest.isEmpty()) head else head + CategoryTotal(null, rest.map { it.second }.reduce { a, b -> a + b })
    }
    return CashFlowChartsUiState(
        loading = false,
        month = month,
        display = display,
        incomeVsExpenses = bars,
        balance = balance,
        expensesByCategory = top(EntryKind.EXPENSE),
        incomeByCategory = top(EntryKind.INCOME),
    )
}

@HiltViewModel
class CashFlowChartsViewModel @Inject constructor(
    cash: CashRepository,
    categories: CategoryRepository,
    rates: ExchangeRateRepository,
    settings: SettingsRepository,
    private val time: TimeSource,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.from(time.today()))

    val state: StateFlow<CashFlowChartsUiState> = combine(cash.ledger, categories.observeAll(), rates.rates, settings.settings, month) { l, c, r, s, m ->
        cashFlowCharts(l, c, r, s.displayCurrency, m, time.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CashFlowChartsUiState())

    fun setMonth(value: YearMonth) {
        month.value = value
    }
}

@Composable
fun CashFlowChartsScreen(onBack: () -> Unit, viewModel: CashFlowChartsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CashFlowChartsContent(state, onBack, viewModel::setMonth)
}

@Composable
fun CashFlowChartsContent(state: CashFlowChartsUiState, onBack: () -> Unit, onMonth: (YearMonth) -> Unit) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    DetailScaffold(title = stringResource(R.string.charts_title), onBack = onBack) {
        if (state.loading) return@DetailScaffold
        MonthSwitcher(state.month, onMonth)

        SectionHeader(stringResource(R.string.charts_income_vs_expenses))
        FinanceCard(Modifier.fillMaxWidth()) {
            val months = state.incomeVsExpenses.map { it.first }
            FinanceColumnChart(
                series = listOf(
                    state.incomeVsExpenses.map { it.second.toBigDecimal().toDouble() } to c.positive,
                    state.incomeVsExpenses.map { it.third.toBigDecimal().toDouble() } to c.negative,
                ),
                xLabel = { i -> months.getOrNull(i)?.let { formats.dates.monthShort(it) } ?: "" },
                yLabel = { v -> formats.money.formatShort((v * 100).toLong(), state.display) },
                summary = stringResource(R.string.charts_summary_bars),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                LegendDot(c.positive, stringResource(R.string.cashflow_income))
                LegendDot(c.negative, stringResource(R.string.cashflow_expenses))
            }
        }

        SectionHeader(stringResource(R.string.charts_balance))
        FinanceCard(Modifier.fillMaxWidth()) {
            if (state.balance.size < 2) {
                Text(stringResource(R.string.charts_empty), style = FinanceTheme.type.body, color = c.textSecondary)
            } else {
                val months = state.balance.map { it.first }
                FinanceLineChart(
                    series = listOf(LineSeries(state.balance.mapIndexed { i, (_, m) -> i.toDouble() to m.toBigDecimal().toDouble() }, c.textPrimary, area = true)),
                    xLabel = { v -> months.getOrNull(v.toInt())?.let { formats.dates.monthShort(it) } ?: "" },
                    yLabel = { v -> formats.money.formatShort((v * 100).toLong(), state.display) },
                    summary = stringResource(R.string.charts_summary_balance),
                )
            }
        }

        CategorySection(stringResource(R.string.charts_expenses_by_category), state.expensesByCategory, c.negative)
        CategorySection(stringResource(R.string.charts_income_by_category), state.incomeByCategory, c.positive)
    }
}

@Composable
private fun CategorySection(title: String, items: List<CategoryTotal>, color: androidx.compose.ui.graphics.Color) {
    val formats = LocalFormats.current
    SectionHeader(title)
    FinanceCard(Modifier.fillMaxWidth()) {
        if (items.isEmpty()) {
            Text(stringResource(R.string.charts_empty), style = FinanceTheme.type.body, color = FinanceTheme.colors.textSecondary)
        } else {
            val max = items.maxOf { it.total.money.minor }.coerceAtLeast(1)
            CategoryBars(
                items.map {
                    BarItem(
                        label = it.category?.displayName() ?: stringResource(R.string.charts_other),
                        value = (if (it.total.incomplete) "≈ " else "") + formats.money.format(it.total.money),
                        share = it.total.money.minor.toFloat() / max,
                    )
                },
                color,
            )
        }
    }
}
