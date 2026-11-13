package io.github.luannamorim.myfinance.ui.feature.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luannamorim.myfinance.core.format.AmountInput
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.data.repository.PortfolioRepository
import io.github.luannamorim.myfinance.data.repository.TransactionDraft
import io.github.luannamorim.myfinance.domain.cash.Recurrence
import io.github.luannamorim.myfinance.domain.cash.RecurrenceRule
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.model.Category
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.domain.model.RecurrenceUnit
import io.github.luannamorim.myfinance.ui.common.ActionError
import io.github.luannamorim.myfinance.ui.common.TransferTarget
import io.github.luannamorim.myfinance.ui.common.suggestAssetAmount
import io.github.luannamorim.myfinance.ui.common.toActionError
import io.github.luannamorim.myfinance.ui.common.transferTarget
import io.github.luannamorim.myfinance.ui.common.transferTargets
import io.github.luannamorim.myfinance.ui.navigation.TransactionEditorRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** What the editor is editing. */
enum class EditorMode {
    /** A new one-time or recurring transaction. */
    NEW,

    /** A recurring template (changes apply to pending occurrences from today on). */
    TEMPLATE,

    /** A one-time (standalone) transaction. */
    STANDALONE,

    /** One generated occurrence: name, amount and due date only. */
    OCCURRENCE,
}

sealed interface RepeatChoice {
    data object Never : RepeatChoice
    data class Preset(val preset: RecurrenceRule.Preset) : RepeatChoice
    data object Custom : RepeatChoice
}

data class TransactionEditorUiState(
    val loading: Boolean = true,
    val mode: EditorMode = EditorMode.NEW,
    val kind: EntryKind = EntryKind.EXPENSE,
    val name: String = "",
    val digits: String = "",
    val currency: Currency = Currency.BRL,
    val categoryId: Long? = null,
    val categories: List<Category> = emptyList(),
    /** First due date (new), date (one-time) or due date (single occurrence). */
    val date: LocalDate = LocalDate.MIN,
    val repeat: RepeatChoice = RepeatChoice.Never,
    val customInterval: Int = 1,
    val customUnit: RecurrenceUnit = RecurrenceUnit.MONTH,
    val endDate: LocalDate? = null,
    val description: String = "",
    val alreadySettled: Boolean = true,
    val active: Boolean = true,
    /** Template edits: the next due date shown, and whether the user changed it. */
    val nextDueDate: LocalDate? = null,
    val nextDueChanged: Boolean = false,
    val customizedCount: Int = 0,
    /** A confirmed or cancelled one-time transaction keeps its state; only its details change. */
    val settledAlready: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
    val nameError: Boolean = false,
    val amountError: Boolean = false,
    val categoryError: Boolean = false,
    val done: Boolean = false,
    /** Transfers: the asset, the assets to choose from, and what the asset received when currencies differ. */
    val assetId: Long? = null,
    val assets: List<TransferTarget> = emptyList(),
    val assetError: Boolean = false,
    /** The amount on the asset side as typed; null while the suggestion (at the date's rate) is used. */
    val assetDigits: String? = null,
    val rates: ExchangeRates = ExchangeRates.EMPTY,
    val error: ActionError? = null,
) {
    val isTransfer: Boolean get() = kind.isTransfer

    val target: TransferTarget? get() = assets.firstOrNull { it.assetId == assetId }

    /** A done transfer keeps its asset and direction (undo it first); a done income or expense can't become one. */
    val kindLocked: Boolean get() = settledAlready && isTransfer

    /** Settling a transfer between currencies records what the asset received or gave. */
    val needsAssetAmount: Boolean
        get() = isTransfer && canSettle && alreadySettled && target.let { it != null && it.currency != currency }

    /** The asset-side amount: typed, or suggested at the rate of the date. */
    val assetAmountDigits: String
        get() {
            assetDigits?.let { return it }
            val t = target ?: return ""
            val minor = AmountInput.minorOf(digits) ?: return ""
            return rates.suggestAssetAmount(Money(minor, currency), t.currency, date)?.let { AmountInput.digitsOf(it.minor) }.orEmpty()
        }

    val isRecurring: Boolean get() = mode == EditorMode.TEMPLATE || (mode == EditorMode.NEW && repeat != RepeatChoice.Never)

    val rule: RecurrenceRule?
        get() = when (val r = repeat) {
            RepeatChoice.Never -> null
            is RepeatChoice.Preset -> r.preset.rule
            RepeatChoice.Custom -> RecurrenceRule(customUnit, customInterval.coerceIn(1, RecurrenceRule.MAX_INTERVAL))
        }

    /** "Already paid/received" applies to new or pending one-time transactions dated today or earlier. */
    val canSettle: Boolean
        get() = (mode == EditorMode.NEW && repeat == RepeatChoice.Never || mode == EditorMode.STANDALONE && !settledAlready) && !date.isAfter(today)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cash: CashRepository,
    categories: CategoryRepository,
    portfolio: PortfolioRepository,
    rates: ExchangeRateRepository,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<TransactionEditorRoute>()
    private val form = MutableStateFlow(TransactionEditorUiState(kind = route.kind, today = time.today(), date = time.today()))

    private val categoryList = form.map { it.kind }.distinctUntilChanged().flatMapLatest { categories.observe(it, includeArchived = true) }

    val state: StateFlow<TransactionEditorUiState> = combine(form, categoryList, portfolio.data, rates.rates) { f, list, assets, r ->
        // Archived categories are hidden, except the one already chosen (editing old data); closed assets too.
        val open = assets.transferTargets()
        val chosen = assets.transferTarget(f.assetId)?.takeIf { c -> open.none { it.assetId == c.assetId } }
        f.copy(categories = list.filter { !it.archived || it.id == f.categoryId }, assets = open + listOfNotNull(chosen), rates = r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val today = time.today()
        when {
            route.templateId != 0L -> {
                val t = cash.template(route.templateId) ?: return form.update { it.copy(done = true) }
                val next = cash.snapshot().occurrences
                    .filter { it.templateId == t.id && it.state == OccurrenceState.PENDING && !it.dueDate.isBefore(today) }
                    .minOfOrNull { it.dueDate }
                    ?: Recurrence.firstOnOrAfter(t.anchorDate, t.rule, today)
                val preset = t.rule.presetOrNull()
                form.update {
                    it.copy(
                        loading = false, mode = EditorMode.TEMPLATE, kind = t.kind, name = t.name, digits = AmountInput.digitsOf(t.expected.minor),
                        currency = t.expected.currency, categoryId = t.categoryId, assetId = t.assetId, date = t.startDate,
                        repeat = if (preset != null) RepeatChoice.Preset(preset) else RepeatChoice.Custom,
                        customInterval = t.rule.interval, customUnit = t.rule.unit, endDate = t.endDate, description = t.description.orEmpty(),
                        active = t.active, nextDueDate = next, customizedCount = cash.customizedReplaceableCount(t.id), today = today,
                    )
                }
            }
            route.occurrenceId != 0L -> {
                val o = cash.occurrence(route.occurrenceId) ?: return form.update { it.copy(done = true) }
                form.update {
                    it.copy(
                        loading = false, mode = if (o.standalone) EditorMode.STANDALONE else EditorMode.OCCURRENCE,
                        kind = o.kind, name = o.name, digits = AmountInput.digitsOf(o.expected.minor), currency = o.expected.currency,
                        categoryId = o.categoryId, assetId = o.assetId, date = o.dueDate, description = o.description.orEmpty(),
                        alreadySettled = false, settledAlready = o.state != OccurrenceState.PENDING, today = today,
                    )
                }
            }
            else -> {
                val currency = settings.current().defaultCurrency
                form.update { it.copy(loading = false, currency = currency, today = today, date = today) }
            }
        }
    }

    fun setKind(kind: EntryKind) = form.update {
        when {
            it.kind == kind -> it
            // Switching direction keeps the chosen asset.
            it.kind.isTransfer && kind.isTransfer -> it.copy(kind = kind)
            else -> it.copy(kind = kind, categoryId = null, categoryError = false, assetId = null, assetError = false, assetDigits = null)
        }
    }
    fun setAsset(id: Long) = form.update { it.copy(assetId = id, assetError = false, assetDigits = null) }
    fun setAssetDigits(digits: String) = form.update { it.copy(assetDigits = digits) }
    fun dismissError() = form.update { it.copy(error = null) }
    fun setName(name: String) = form.update { it.copy(name = name, nameError = false) }
    fun setDigits(digits: String) = form.update { it.copy(digits = digits, amountError = false) }
    fun setCurrency(currency: Currency) = form.update { it.copy(currency = currency) }
    fun setCategory(id: Long) = form.update { it.copy(categoryId = id, categoryError = false) }
    fun setDate(date: LocalDate) = form.update { it.copy(date = date) }
    fun setRepeat(repeat: RepeatChoice) = form.update { it.copy(repeat = repeat) }
    fun setCustomInterval(interval: Int) = form.update { it.copy(customInterval = interval.coerceIn(1, RecurrenceRule.MAX_INTERVAL)) }
    fun setCustomUnit(unit: RecurrenceUnit) = form.update { it.copy(customUnit = unit) }
    fun setEndDate(date: LocalDate?) = form.update { it.copy(endDate = date) }
    fun setDescription(text: String) = form.update { it.copy(description = text) }
    fun setAlreadySettled(value: Boolean) = form.update { it.copy(alreadySettled = value) }
    fun setNextDueDate(date: LocalDate) = form.update { it.copy(nextDueDate = date, nextDueChanged = true) }

    /** Validates and saves; returns false (and flags the fields) when something is missing. */
    fun save(): Boolean {
        // The form is the source of truth; assets and rates come from the combined state.
        val s = form.value.copy(assets = state.value.assets, rates = state.value.rates)
        val minor = AmountInput.minorOf(s.digits) ?: 0
        // A transfer's name is optional: it defaults to the asset's.
        val nameError = s.name.isBlank() && (!s.isTransfer || s.mode == EditorMode.OCCURRENCE)
        val amountError = minor <= 0
        val categoryError = !s.isTransfer && s.categoryId == null && s.mode != EditorMode.OCCURRENCE
        val assetMinor = if (s.needsAssetAmount) AmountInput.minorOf(s.assetAmountDigits) ?: 0 else 0
        val assetError = s.isTransfer && s.mode != EditorMode.OCCURRENCE && (s.assetId == null || s.needsAssetAmount && assetMinor <= 0)
        if (nameError || amountError || categoryError || assetError) {
            form.update { it.copy(nameError = nameError, amountError = amountError, categoryError = categoryError, assetError = assetError) }
            return false
        }
        val amount = Money(minor, s.currency)
        viewModelScope.launch {
            val saved = runCatching {
                when (s.mode) {
                    EditorMode.OCCURRENCE -> cash.editOccurrence(route.occurrenceId, s.name, amount, s.date)
                    else -> {
                        val draft = TransactionDraft(
                            name = s.name, categoryId = if (s.isTransfer) null else s.categoryId, kind = s.kind, amount = amount, date = s.date,
                            rule = if (s.isRecurring) s.rule else null, endDate = if (s.isRecurring) s.endDate else null,
                            description = s.description, alreadySettled = s.canSettle && s.alreadySettled,
                            assetId = if (s.isTransfer) s.assetId else null,
                            assetAmount = s.target?.takeIf { s.needsAssetAmount }?.let { Money(assetMinor, it.currency) },
                        )
                        when (s.mode) {
                            EditorMode.NEW -> cash.create(draft)
                            EditorMode.TEMPLATE -> {
                                cash.updateTemplate(route.templateId, draft, nextDueDate = s.nextDueDate.takeIf { s.nextDueChanged })
                                cash.setTemplateActive(route.templateId, s.active)
                            }
                            EditorMode.STANDALONE -> cash.updateStandalone(route.occurrenceId, draft)
                            EditorMode.OCCURRENCE -> Unit
                        }
                    }
                }
            }
            saved.exceptionOrNull()?.let { e -> if (e is CancellationException) throw e }
            form.update { if (saved.isSuccess) it.copy(done = true) else it.copy(error = saved.exceptionOrNull()?.toActionError()) }
        }
        return true
    }

    /** Applied on save: inactive templates stop generating and lose their future pending occurrences. */
    fun setActive(active: Boolean) = form.update { it.copy(active = active) }

    /** Deletes the template (future pending occurrences) or the one-time transaction. */
    fun delete() {
        val mode = form.value.mode
        viewModelScope.launch {
            when (mode) {
                EditorMode.TEMPLATE -> cash.deleteTemplate(route.templateId)
                EditorMode.STANDALONE -> cash.deleteOccurrence(route.occurrenceId)
                else -> return@launch
            }
            form.update { it.copy(done = true) }
        }
    }
}
