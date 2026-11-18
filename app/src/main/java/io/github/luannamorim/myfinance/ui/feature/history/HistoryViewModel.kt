package io.github.luannamorim.myfinance.ui.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.domain.cash.CashLedger
import io.github.luannamorim.myfinance.domain.cash.History
import io.github.luannamorim.myfinance.domain.cash.HistoryEntry
import io.github.luannamorim.myfinance.domain.cash.HistoryFilter
import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.model.AdjustmentKind
import io.github.luannamorim.myfinance.domain.model.Category
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.ui.common.OccurrenceRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** A history line ready to show. */
sealed interface HistoryItem {
    data class Transaction(val row: OccurrenceRow) : HistoryItem
    data class Adjustment(val entry: HistoryEntry.Adjustment) : HistoryItem
}

data class HistoryUiState(
    val loading: Boolean = true,
    val filter: HistoryFilter = HistoryFilter(YearMonth.now()),
    /** The month's lines, newest first. */
    val items: List<HistoryItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val hasAnyData: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
)

fun historyState(
    ledger: CashLedger,
    categories: List<Category>,
    filter: HistoryFilter,
    names: Map<Long, String>,
    today: LocalDate,
    display: Currency = Currency.BRL,
    rates: ExchangeRates = ExchangeRates.EMPTY,
): HistoryUiState {
    val byId = categories.associateBy { it.id }
    val converter = Converter(rates)
    val entries = History.filter(ledger.occurrences, ledger.adjustments, filter) { o ->
        listOfNotNull(o.name, names[o.categoryId] ?: byId[o.categoryId]?.name, o.description, o.note).joinToString(" ")
    }
    return HistoryUiState(
        loading = false,
        filter = filter,
        items = entries.map {
            when (it) {
                is HistoryEntry.Transaction -> HistoryItem.Transaction(OccurrenceRow.of(it.occurrence, byId, today, display, converter))
                is HistoryEntry.Adjustment -> HistoryItem.Adjustment(it)
            }
        },
        categories = categories,
        // An opening balance alone is no history yet: it is the starting point, not a line.
        hasAnyData = ledger.occurrences.isNotEmpty() || ledger.adjustments.any { it.kind == AdjustmentKind.ADJUSTMENT },
        today = today,
    )
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    cash: CashRepository,
    categories: CategoryRepository,
    rates: ExchangeRateRepository,
    settings: SettingsRepository,
    private val time: TimeSource,
) : ViewModel() {

    /** Opens on the current month, like Cash Flow. */
    private val filter = MutableStateFlow(HistoryFilter(YearMonth.from(time.today())))

    /** Localized category names (the screen resolves predefined ones), so search matches what the user sees. */
    private val names = MutableStateFlow<Map<Long, String>>(emptyMap())

    /** The display currency and the rates rows convert with (combine takes at most five flows). */
    private val conversion = combine(settings.settings, rates.rates) { s, r -> s.displayCurrency to r }

    val state: StateFlow<HistoryUiState> = combine(cash.ledger, categories.observeAll(), filter, names, conversion) { ledger, cats, f, n, (display, r) ->
        historyState(ledger, cats, f, n, time.today(), display, r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setCategoryNames(value: Map<Long, String>) {
        names.value = value
    }

    fun update(transform: (HistoryFilter) -> HistoryFilter) = filter.update(transform)
}
