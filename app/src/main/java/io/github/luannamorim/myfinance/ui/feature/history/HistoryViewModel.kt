package io.github.luannamorim.myfinance.ui.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.domain.cash.CashLedger
import io.github.luannamorim.myfinance.domain.cash.History
import io.github.luannamorim.myfinance.domain.cash.HistoryEntry
import io.github.luannamorim.myfinance.domain.cash.HistoryFilter
import io.github.luannamorim.myfinance.domain.model.Category
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
    val filter: HistoryFilter = HistoryFilter(),
    val months: List<Pair<YearMonth, List<HistoryItem>>> = emptyList(),
    val categories: List<Category> = emptyList(),
    val hasAnyData: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
)

fun historyState(ledger: CashLedger, categories: List<Category>, filter: HistoryFilter, names: Map<Long, String>, today: LocalDate): HistoryUiState {
    val byId = categories.associateBy { it.id }
    val entries = History.filter(ledger.occurrences, ledger.adjustments, filter, today) { o ->
        listOfNotNull(o.name, names[o.categoryId] ?: byId[o.categoryId]?.name, o.description, o.note).joinToString(" ")
    }
    return HistoryUiState(
        loading = false,
        filter = filter,
        months = History.byMonth(entries).map { (month, list) ->
            month to list.map {
                when (it) {
                    is HistoryEntry.Transaction -> HistoryItem.Transaction(OccurrenceRow.of(it.occurrence, byId, today))
                    is HistoryEntry.Adjustment -> HistoryItem.Adjustment(it)
                }
            }
        },
        categories = categories,
        hasAnyData = ledger.occurrences.isNotEmpty() || ledger.adjustments.isNotEmpty(),
        today = today,
    )
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    cash: CashRepository,
    categories: CategoryRepository,
    private val time: TimeSource,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter())

    /** Localized category names (the screen resolves predefined ones), so search matches what the user sees. */
    private val names = MutableStateFlow<Map<Long, String>>(emptyMap())

    val state: StateFlow<HistoryUiState> = combine(cash.ledger, categories.observeAll(), filter, names) { ledger, cats, f, n ->
        historyState(ledger, cats, f, n, time.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setCategoryNames(value: Map<Long, String>) {
        names.value = value
    }

    fun update(transform: (HistoryFilter) -> HistoryFilter) = filter.update(transform)

    fun clear() {
        filter.value = HistoryFilter()
    }
}
