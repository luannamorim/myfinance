package io.github.luannamorim.myfinance.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luannamorim.myfinance.core.format.AmountInput
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.data.repository.OpeningAfterAdjustmentException
import io.github.luannamorim.myfinance.domain.cash.CashCalculator
import io.github.luannamorim.myfinance.domain.model.AdjustmentKind
import io.github.luannamorim.myfinance.domain.model.CashAdjustment
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The opening-balance form of one currency. */
data class OpeningForm(val digits: String = "", val negative: Boolean = false, val date: LocalDate, val saved: Boolean = false, val error: Boolean = false)

data class AdjustForm(val currency: Currency = Currency.BRL, val digits: String = "", val negative: Boolean = false, val date: LocalDate, val note: String = "")

enum class BalanceMessage { OPENING_AFTER_ADJUSTMENT, NO_DIFFERENCE, NEEDS_OPENING }

data class BalanceUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.MIN,
    val openings: Map<Currency, CashAdjustment?> = emptyMap(),
    val openingForms: Map<Currency, OpeningForm> = emptyMap(),
    val balances: Map<Currency, Money> = emptyMap(),
    val adjustments: List<CashAdjustment> = emptyList(),
    val adjust: AdjustForm = AdjustForm(date = LocalDate.MIN),
    val message: BalanceMessage? = null,
    val adjustSaved: Boolean = false,
) {
    val adjustTarget: Money? get() = AmountInput.minorOf(adjust.digits)?.let { Money(if (adjust.negative) -it else it, adjust.currency) }

    /** Real balance − balance on the adjustment date (the value that will be stored). */
    val adjustDifference: Money? get() = adjustTarget?.let { target -> balances[target.currency]?.let { target - it } }
}

/** Opening balances and balance adjustments (Settings → Cash). */
@HiltViewModel
class BalanceViewModel @Inject constructor(
    private val cash: CashRepository,
    private val time: TimeSource,
) : ViewModel() {

    private val today = time.today()
    private val local = MutableStateFlow(
        BalanceUiState(
            today = today,
            openingForms = Currency.entries.associateWith { OpeningForm(date = today) },
            adjust = AdjustForm(date = today),
        ),
    )
    init {
        viewModelScope.launch {
            val ledger = cash.snapshot()
            val forms = Currency.entries.associateWith { currency ->
                val o = CashCalculator.opening(ledger, currency)
                if (o == null) OpeningForm(date = today) else OpeningForm(AmountInput.digitsOf(Math.abs(o.amount.minor)), o.amount.isNegative, o.date)
            }
            local.update { it.copy(openingForms = forms) }
        }
    }

    val state: StateFlow<BalanceUiState> = combine(cash.ledger, local) { ledger, l ->
        val openings = Currency.entries.associateWith { CashCalculator.opening(ledger, it) }
        l.copy(
            loading = false,
            openings = openings,
            balances = Currency.entries.associateWith { CashCalculator.balance(it, l.adjust.date, ledger) },
            adjustments = ledger.adjustments.filter { it.kind == AdjustmentKind.ADJUSTMENT }.sortedByDescending { it.date },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun editOpening(currency: Currency, transform: (OpeningForm) -> OpeningForm) = local.update { s ->
        s.copy(openingForms = s.openingForms + (currency to transform(s.openingForms.getValue(currency)).copy(saved = false, error = false)))
    }

    fun saveOpening(currency: Currency) {
        val form = local.value.openingForms.getValue(currency)
        val minor = AmountInput.minorOf(form.digits) ?: 0
        viewModelScope.launch {
            try {
                cash.setOpening(Money(if (form.negative) -minor else minor, currency), form.date)
                local.update { s -> s.copy(openingForms = s.openingForms + (currency to form.copy(saved = true))) }
            } catch (_: OpeningAfterAdjustmentException) {
                local.update { s -> s.copy(openingForms = s.openingForms + (currency to form.copy(error = true)), message = BalanceMessage.OPENING_AFTER_ADJUSTMENT) }
            }
        }
    }

    fun editAdjust(transform: (AdjustForm) -> AdjustForm) = local.update { it.copy(adjust = transform(it.adjust), adjustSaved = false, message = null) }

    fun saveAdjustment() {
        val s = state.value
        val target = s.adjustTarget ?: return
        val opening = s.openings[target.currency]
        if (opening == null) {
            local.update { it.copy(message = BalanceMessage.NEEDS_OPENING) }
            return
        }
        viewModelScope.launch {
            val stored = cash.addAdjustment(target, s.adjust.date, s.adjust.note)
            local.update {
                if (stored == null) it.copy(message = BalanceMessage.NO_DIFFERENCE)
                else it.copy(adjust = AdjustForm(currency = target.currency, date = today), adjustSaved = true)
            }
        }
    }

    fun deleteAdjustment(id: Long) = viewModelScope.launch { cash.deleteAdjustment(id) }

    fun dismissMessage() = local.update { it.copy(message = null) }
}
