package io.github.luannamorim.myfinance.ui.feature.cashflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.domain.cash.CashCalculator
import io.github.luannamorim.myfinance.domain.cash.CashLedger
import io.github.luannamorim.myfinance.domain.cash.MonthSummary
import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.domain.model.Category
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.ExchangeRate
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.ui.common.OccurrenceRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CashFlowUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    val display: Currency = Currency.BRL,
    val summary: MonthSummary? = null,
    /** Cash today (current month) or at the end of a past month. */
    val balance: Total? = null,
    val balanceIsToday: Boolean = true,
    /** Projected balance at the end of [month] (actual balance for past months). */
    val projectedEnd: Total? = null,
    val overdue: List<OccurrenceRow> = emptyList(),
    val pendingBills: List<OccurrenceRow> = emptyList(),
    val upcomingIncome: List<OccurrenceRow> = emptyList(),
    val nextMonth: MonthSummary? = null,
    val nextMonthProjected: Total? = null,
    val rate: ExchangeRate? = null,
    /** Some amount is in another currency than [display] (show the rate). */
    val usesConversion: Boolean = false,
    val empty: Boolean = true,
)

/** Builds the Cash Flow state from the ledger. All figures come from CashCalculator. */
fun cashFlowState(
    ledger: CashLedger,
    categories: List<Category>,
    rates: ExchangeRates,
    display: Currency,
    month: YearMonth,
    today: LocalDate,
): CashFlowUiState {
    val converter = Converter(rates)
    val byId = categories.associateBy { it.id }
    val current = YearMonth.from(today)
    val past = month.isBefore(current)
    val balance = if (past) {
        CashCalculator.consolidate(CashCalculator.available(ledger, month.atEndOfMonth()), display, converter, month.atEndOfMonth())
    } else {
        CashCalculator.consolidate(CashCalculator.available(ledger, today), display, converter)
    }
    val projectedEnd = if (past) balance else CashCalculator.consolidate(CashCalculator.projected(ledger, today, month.atEndOfMonth()), display, converter)
    val next = month.plusMonths(1)
    val pendingInMonth = ledger.occurrences.filter {
        it.state == OccurrenceState.PENDING && YearMonth.from(it.dueDate) == month && !it.dueDate.isBefore(today)
    }
    fun rows(list: List<io.github.luannamorim.myfinance.domain.model.Occurrence>) = list.sortedBy { it.dueDate }.map { OccurrenceRow.of(it, byId, today) }
    val usesConversion = ledger.occurrences.any { it.expected.currency != display || it.actual?.currency?.let { c -> c != display } == true } ||
        ledger.adjustments.any { it.amount.currency != display }
    return CashFlowUiState(
        loading = false,
        month = month,
        today = today,
        display = display,
        summary = CashCalculator.monthSummary(ledger, month, today, display, converter),
        balance = balance,
        balanceIsToday = !past,
        projectedEnd = projectedEnd,
        overdue = if (month == current) rows(ledger.occurrences.filter { it.state == OccurrenceState.PENDING && it.dueDate.isBefore(today) }) else emptyList(),
        pendingBills = rows(pendingInMonth.filter { it.kind == EntryKind.EXPENSE }),
        upcomingIncome = rows(pendingInMonth.filter { it.kind == EntryKind.INCOME }),
        nextMonth = CashCalculator.monthSummary(ledger, next, today, display, converter),
        nextMonthProjected = CashCalculator.consolidate(CashCalculator.projected(ledger, today, next.atEndOfMonth()), display, converter),
        rate = rates.latest(),
        usesConversion = usesConversion,
        empty = ledger.occurrences.isEmpty() && ledger.adjustments.isEmpty(),
    )
}

@HiltViewModel
class CashFlowViewModel @Inject constructor(
    cash: CashRepository,
    categories: CategoryRepository,
    rates: ExchangeRateRepository,
    settings: SettingsRepository,
    private val time: TimeSource,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(time.today()))

    val state: StateFlow<CashFlowUiState> = combine(cash.ledger, categories.observeAll(), rates.rates, settings.settings, month) { ledger, cats, r, s, m ->
        cashFlowState(ledger, cats, r, s.displayCurrency, m, time.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CashFlowUiState(month = month.value, today = time.today()))

    fun setMonth(value: YearMonth) {
        month.value = value
    }
}
