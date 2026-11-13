package io.github.luannamorim.myfinance.ui.feature.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.domain.cash.DisplayStatus
import io.github.luannamorim.myfinance.domain.cash.OccurrenceStatus
import io.github.luannamorim.myfinance.domain.model.Category
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.Occurrence
import io.github.luannamorim.myfinance.domain.model.TransactionTemplate
import io.github.luannamorim.myfinance.ui.navigation.OccurrenceDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class OccurrenceDetailUiState(
    val loading: Boolean = true,
    val occurrence: Occurrence? = null,
    val status: DisplayStatus = DisplayStatus.UPCOMING,
    val category: Category? = null,
    val template: TransactionTemplate? = null,
    val today: LocalDate = LocalDate.MIN,
    val confirming: Boolean = false,
    /** True once the occurrence no longer exists (deleted). */
    val gone: Boolean = false,
) {
    /** Actual − expected, when both are in the same currency. */
    val difference: Money?
        get() {
            val o = occurrence ?: return null
            val actual = o.actual ?: return null
            return if (actual.currency == o.expected.currency) actual - o.expected else null
        }
}

@HiltViewModel
class OccurrenceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cash: CashRepository,
    categories: CategoryRepository,
    private val time: TimeSource,
) : ViewModel() {

    private val id = savedStateHandle.toRoute<OccurrenceDetailRoute>().id
    private val ui = MutableStateFlow(OccurrenceDetailUiState())

    val state: StateFlow<OccurrenceDetailUiState> = combine(cash.ledger, categories.observeAll(), ui) { ledger, cats, local ->
        val today = time.today()
        val occurrence = ledger.occurrences.firstOrNull { it.id == id }
        if (occurrence == null) {
            local.copy(loading = false, gone = true, today = today)
        } else {
            local.copy(
                loading = false,
                occurrence = occurrence,
                status = OccurrenceStatus.of(occurrence, today),
                category = cats.firstOrNull { it.id == occurrence.categoryId },
                template = occurrence.templateId?.let { t -> ledger.templates.firstOrNull { it.id == t } },
                today = today,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OccurrenceDetailUiState())

    fun startConfirm() = ui.update { it.copy(confirming = true) }
    fun dismissConfirm() = ui.update { it.copy(confirming = false) }

    fun confirm(actual: Money, date: LocalDate, note: String?) {
        ui.update { it.copy(confirming = false) }
        viewModelScope.launch { cash.confirm(id, actual, date, note) }
    }

    fun unconfirm() = viewModelScope.launch { cash.unconfirm(id) }
    fun cancel() = viewModelScope.launch { cash.cancel(id) }
    fun restore() = viewModelScope.launch { cash.restore(id) }
    fun delete() = viewModelScope.launch { cash.deleteOccurrence(id) }
}
