package io.github.luannamorim.myfinance.ui.feature.portfolio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.data.repository.PortfolioRepository
import io.github.luannamorim.myfinance.domain.cash.CashLedger
import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.domain.model.AssetCategory
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.networth.NetWorth
import io.github.luannamorim.myfinance.domain.networth.NetWorthCalculator
import io.github.luannamorim.myfinance.domain.portfolio.Allocation
import io.github.luannamorim.myfinance.domain.portfolio.Period
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioCalculator
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioData
import io.github.luannamorim.myfinance.domain.portfolio.Series
import io.github.luannamorim.myfinance.domain.portfolio.SeriesPoint
import io.github.luannamorim.myfinance.domain.portfolio.Slice
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.common.displayName
import io.github.luannamorim.myfinance.ui.components.AmountDisplay
import io.github.luannamorim.myfinance.ui.components.AmountTone
import io.github.luannamorim.myfinance.ui.components.BarItem
import io.github.luannamorim.myfinance.ui.components.CategoryBars
import io.github.luannamorim.myfinance.ui.components.DetailScaffold
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.FinanceChip
import io.github.luannamorim.myfinance.ui.components.FinanceLineChart
import io.github.luannamorim.myfinance.ui.components.LegendDot
import io.github.luannamorim.myfinance.ui.components.LineSeries
import io.github.luannamorim.myfinance.ui.components.SectionHeader
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class PortfolioChartsUiState(
    val loading: Boolean = true,
    val period: Period = Period.Y1,
    val display: Currency = Currency.BRL,
    val netWorth: List<Pair<LocalDate, NetWorth>> = emptyList(),
    val portfolio: List<SeriesPoint> = emptyList(),
    /** Gain in the period: Δvalue − net flows, in the display currency (currency effect included). */
    val periodGain: Total? = null,
    val allocation: List<Pair<Slice, AssetCategory?>> = emptyList(),
)

/** Gain of every asset with records up to [date] (closed ones keep their realized gain). */
private fun gainAt(data: PortfolioData, date: LocalDate, to: Currency, converter: Converter): Total =
    data.assets.fold(Total.zero(to)) { acc, asset ->
        val flows = data.flowsOf(asset)
        val s = PortfolioCalculator.snapshot(asset, flows, data.valuationsOf(asset), date) ?: return@fold acc
        acc + PortfolioCalculator.convertedSnapshot(s, flows, date, to, converter).gain
    }

fun portfolioCharts(data: PortfolioData, cash: CashLedger, rates: ExchangeRates, display: Currency, period: Period, today: LocalDate): PortfolioChartsUiState {
    val converter = Converter(rates)
    val first = NetWorthCalculator.firstRecord(cash, data)
    val netDates = first?.let { Series.sampleDates(period, it, today) }.orEmpty()
    val portfolio = Series.portfolio(data, period, today, display, converter)
    val start = period.months?.let { today.minusMonths(it) }
    val periodGain = if (portfolio.isEmpty()) null else gainAt(data, today, display, converter) - (start?.let { gainAt(data, it, display, converter) } ?: Total.zero(display))
    val categories = data.categories.associateBy { it.id }
    return PortfolioChartsUiState(
        loading = false,
        period = period,
        display = display,
        netWorth = NetWorthCalculator.series(netDates, cash, data, display, converter),
        portfolio = portfolio,
        periodGain = periodGain,
        allocation = Allocation.of(data, today, display, converter).map { it to categories[it.categoryId] },
    )
}

@HiltViewModel
class PortfolioChartsViewModel @Inject constructor(
    portfolio: PortfolioRepository,
    cash: CashRepository,
    rates: ExchangeRateRepository,
    settings: SettingsRepository,
    private val time: TimeSource,
) : ViewModel() {
    private val period = MutableStateFlow(Period.Y1)

    val state: StateFlow<PortfolioChartsUiState> = combine(portfolio.data, cash.ledger, rates.rates, settings.settings, period) { p, c, r, s, per ->
        portfolioCharts(p, c, r, s.displayCurrency, per, time.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortfolioChartsUiState())

    fun setPeriod(value: Period) {
        period.value = value
    }
}

@Composable
fun PortfolioChartsScreen(onBack: () -> Unit, viewModel: PortfolioChartsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PortfolioChartsContent(state, onBack, viewModel::setPeriod)
}

@Composable
fun PortfolioChartsContent(state: PortfolioChartsUiState, onBack: () -> Unit, onPeriod: (Period) -> Unit) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    // X is the sample index (month ends, or weeks for 1M): labels show the sample dates, evenly spaced.
    fun labels(dates: List<LocalDate>): (Double) -> String = { v -> dates.getOrNull(v.toInt())?.let(formats.dates::dayMonth).orEmpty() }
    val moneyLabel: (Double) -> String = { v -> formats.money.formatShort((v * 100).toLong(), state.display) }
    DetailScaffold(title = stringResource(R.string.pcharts_title), onBack = onBack) {
        if (state.loading) return@DetailScaffold
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            listOf(
                Period.M1 to R.string.pcharts_period_1m,
                Period.M3 to R.string.pcharts_period_3m,
                Period.M6 to R.string.pcharts_period_6m,
                Period.Y1 to R.string.pcharts_period_1y,
                Period.ALL to R.string.pcharts_period_all,
            ).forEach { (p, label) -> FinanceChip(stringResource(label), selected = state.period == p, onClick = { onPeriod(p) }) }
        }

        SectionHeader(stringResource(R.string.pcharts_net_worth))
        ChartCard(state.netWorth.size) {
            FinanceLineChart(
                series = listOf(LineSeries(state.netWorth.mapIndexed { i, (_, nw) -> i.toDouble() to nw.total.money.toBigDecimal().toDouble() }, c.accent, area = true)),
                xLabel = labels(state.netWorth.map { it.first }),
                yLabel = moneyLabel,
                summary = stringResource(R.string.pcharts_net_worth_summary),
            )
        }

        SectionHeader(stringResource(R.string.pcharts_value_vs_invested))
        ChartCard(state.portfolio.size) {
            FinanceLineChart(
                series = listOf(
                    LineSeries(state.portfolio.mapIndexed { i, p -> i.toDouble() to p.invested.money.toBigDecimal().toDouble() }, c.chart[3]),
                    LineSeries(state.portfolio.mapIndexed { i, p -> i.toDouble() to p.value.money.toBigDecimal().toDouble() }, c.accent, area = true),
                ),
                xLabel = labels(state.portfolio.map { it.date }),
                yLabel = moneyLabel,
                summary = stringResource(R.string.asset_chart_summary),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                LegendDot(c.accent, stringResource(R.string.portfolio_value))
                LegendDot(c.chart[3], stringResource(R.string.portfolio_invested))
            }
        }

        SectionHeader(stringResource(R.string.pcharts_performance))
        state.periodGain?.let { gain ->
            FinanceCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pcharts_period_gain), style = FinanceTheme.type.bodyStrong, color = c.textPrimary, modifier = Modifier.weight(1f))
                    AmountDisplay(gain.money, signed = true, tone = AmountTone.AUTO, approximate = gain.incomplete)
                }
                Text(stringResource(R.string.pcharts_period_gain_hint), style = FinanceTheme.type.caption, color = c.textSecondary)
            }
        }
        ChartCard(state.portfolio.size) {
            FinanceLineChart(
                series = listOf(LineSeries(state.portfolio.mapIndexed { i, p -> i.toDouble() to p.gain.money.toBigDecimal().toDouble() }, c.textPrimary)),
                xLabel = labels(state.portfolio.map { it.date }),
                yLabel = moneyLabel,
                summary = stringResource(R.string.pcharts_performance_summary),
            )
            LegendDot(c.textPrimary, stringResource(R.string.pcharts_cumulative_gain))
        }

        if (state.allocation.isNotEmpty()) {
            SectionHeader(stringResource(R.string.pcharts_allocation))
            FinanceCard(Modifier.fillMaxWidth()) {
                CategoryBars(
                    state.allocation.map { (slice, category) ->
                        BarItem(category?.displayName().orEmpty(), formats.money.formatPercent(slice.share), slice.share.toFloat())
                    },
                    c.accent,
                )
            }
        }
    }
}

@Composable
private fun ChartCard(points: Int, content: @Composable () -> Unit) {
    FinanceCard(Modifier.fillMaxWidth()) {
        if (points < 2) {
            Text(stringResource(R.string.charts_empty), style = FinanceTheme.type.body, color = FinanceTheme.colors.textSecondary)
        } else {
            content()
        }
    }
}
