package io.github.luannamorim.myfinance.ui.feature.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.data.repository.PortfolioRepository
import io.github.luannamorim.myfinance.data.repository.RefreshResult
import io.github.luannamorim.myfinance.domain.cash.CashCalculator
import io.github.luannamorim.myfinance.domain.cash.CashLedger
import io.github.luannamorim.myfinance.domain.cash.MonthSummary
import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.domain.model.Category
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.ExchangeRate
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.domain.networth.NetWorth
import io.github.luannamorim.myfinance.domain.networth.NetWorthCalculator
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioData
import io.github.luannamorim.myfinance.ui.common.ActionError
import io.github.luannamorim.myfinance.ui.common.OccurrenceRow
import io.github.luannamorim.myfinance.ui.common.TransferTarget
import io.github.luannamorim.myfinance.ui.common.toActionError
import io.github.luannamorim.myfinance.ui.common.transferTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class OverviewUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.MIN,
    val display: Currency = Currency.BRL,
    val netWorth: NetWorth? = null,
    val available: Total? = null,
    val projected: Total? = null,
    val month: MonthSummary? = null,
    /** Overdue first, then the rest due this month; at most [OverviewLimits.UPCOMING]. */
    val upcoming: List<OccurrenceRow> = emptyList(),
    val recent: List<OccurrenceRow> = emptyList(),
    val rate: ExchangeRate? = null,
    val usesConversion: Boolean = false,
    val hasOpening: Boolean = false,
    val hasTransactions: Boolean = false,
    val hasAssets: Boolean = false,
    val confirming: OccurrenceRow? = null,
    val updatingRates: Boolean = false,
    val rateResult: RefreshResult? = null,
    /** Assets of transfers, for the confirm sheet. */
    val targets: Map<Long, TransferTarget> = emptyMap(),
    val rates: ExchangeRates = ExchangeRates.EMPTY,
    val error: ActionError? = null,
)

object OverviewLimits {
    const val UPCOMING = 5
    const val RECENT = 5
}

fun overviewState(
    cash: CashLedger,
    portfolio: PortfolioData,
    categories: List<Category>,
    rates: ExchangeRates,
    display: Currency,
    today: LocalDate,
): OverviewUiState {
    val converter = Converter(rates)
    val byId = categories.associateBy { it.id }
    val pending = cash.occurrences.filter { it.state == OccurrenceState.PENDING }
    // Only what is due this month (overdue included): later months belong to the Cash Flow tab.
    val monthEnd = YearMonth.from(today).atEndOfMonth()
    val upcoming = pending.filter { !it.dueDate.isAfter(monthEnd) }.sortedBy { it.dueDate }.take(OverviewLimits.UPCOMING)
    val recent = cash.occurrences.filter { it.state == OccurrenceState.CONFIRMED && it.actualDate != null }
        .sortedWith(compareByDescending<io.github.luannamorim.myfinance.domain.model.Occurrence> { it.actualDate }.thenByDescending { it.id })
        .take(OverviewLimits.RECENT)
    val currencies = (cash.occurrences.flatMap { listOfNotNull(it.expected.currency, it.actual?.currency) } +
        cash.adjustments.map { it.amount.currency } + portfolio.assets.map { it.currency }).toSet()
    return OverviewUiState(
        loading = false,
        today = today,
        display = display,
        netWorth = NetWorthCalculator.at(today, cash, portfolio, display, converter),
        available = CashCalculator.consolidate(CashCalculator.available(cash, today), display, converter),
        projected = CashCalculator.consolidate(CashCalculator.projected(cash, today, YearMonth.from(today).atEndOfMonth()), display, converter),
        month = CashCalculator.monthSummary(cash, YearMonth.from(today), today, display, converter),
        upcoming = upcoming.map { OccurrenceRow.of(it, byId, today) },
        targets = portfolio.assets.mapNotNull { portfolio.transferTarget(it.id) }.associateBy { it.assetId },
        rates = rates,
        recent = recent.map { OccurrenceRow.of(it, byId, today) },
        rate = rates.latest(),
        usesConversion = currencies.any { it != display },
        hasOpening = cash.adjustments.isNotEmpty(),
        hasTransactions = cash.occurrences.isNotEmpty(),
        hasAssets = portfolio.assets.isNotEmpty(),
    )
}

private data class Local(val confirming: Long? = null, val updating: Boolean = false, val result: RefreshResult? = null, val error: ActionError? = null)

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val cash: CashRepository,
    portfolio: PortfolioRepository,
    categories: CategoryRepository,
    private val rates: ExchangeRateRepository,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    private val base = combine(cash.ledger, portfolio.data, categories.observeAll(), rates.rates, settings.settings) { c, p, cats, r, s ->
        overviewState(c, p, cats, r, s.displayCurrency, time.today())
    }

    val state: StateFlow<OverviewUiState> = combine(base, local) { s, l ->
        s.copy(
            confirming = l.confirming?.let { id -> s.upcoming.firstOrNull { it.id == id } },
            updatingRates = l.updating,
            rateResult = l.result,
            error = l.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    fun setDisplay(currency: Currency) {
        viewModelScope.launch { settings.update { it.copy(displayCurrency = currency) } }
    }

    fun startConfirm(id: Long) = local.update { it.copy(confirming = id) }
    fun dismissConfirm() = local.update { it.copy(confirming = null) }

    fun confirm(actual: Money, date: LocalDate, note: String?, assetAmount: Money?) {
        val id = local.value.confirming ?: return
        local.update { it.copy(confirming = null) }
        viewModelScope.launch {
            runCatching { cash.confirm(id, actual, date, note, assetAmount) }
                .onFailure { e -> if (e is CancellationException) throw e else local.update { it.copy(error = e.toActionError()) } }
        }
    }

    fun dismissError() = local.update { it.copy(error = null) }

    fun updateRates() {
        local.update { it.copy(updating = true, result = null) }
        viewModelScope.launch {
            val result = rates.refresh(force = true)
            local.update { it.copy(updating = false, result = result) }
        }
    }
}
