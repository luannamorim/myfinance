package io.github.luannamorim.myfinance.ui.feature.asset

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luannamorim.myfinance.core.format.AmountInput
import io.github.luannamorim.myfinance.core.format.DecimalInput
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.images.ImageKind
import io.github.luannamorim.myfinance.data.images.ImageStore
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.data.repository.AssetDraft
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.data.repository.PortfolioRepository
import io.github.luannamorim.myfinance.data.repository.PriceRepository
import io.github.luannamorim.myfinance.data.repository.QuotePreview
import io.github.luannamorim.myfinance.di.ApplicationScope
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.market.Ticker
import io.github.luannamorim.myfinance.domain.market.TickerMatch
import io.github.luannamorim.myfinance.domain.market.TickerType
import io.github.luannamorim.myfinance.domain.model.AssetCategory
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.NumberFormatStyle
import io.github.luannamorim.myfinance.domain.portfolio.Holdings
import io.github.luannamorim.myfinance.ui.common.ActionError
import io.github.luannamorim.myfinance.ui.common.cashLinkAmount
import io.github.luannamorim.myfinance.ui.common.toActionError
import io.github.luannamorim.myfinance.ui.navigation.AssetEditorRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/** Parses an optional quantity: "10", "0,0125" or "0.00012345" (up to 8 decimals). Empty is null. */
object QuantityInput {
    private val pattern = Regex("""^\d{1,12}([.,]\d{1,8})?$""")
    fun parse(text: String): Result<BigDecimal?> {
        val t = text.trim()
        if (t.isEmpty()) return Result.success(null)
        if (!pattern.matches(t)) return Result.failure(IllegalArgumentException("Invalid quantity"))
        return Result.success(BigDecimal(t.replace(',', '.')))
    }

    fun format(value: BigDecimal?): String = value?.stripTrailingZeros()?.toPlainString().orEmpty()
}

data class AssetEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    /** Editing an asset that follows a quote: its shares come from buys and sells, so there is no quantity field. */
    val editingTracked: Boolean = false,
    val name: String = "",
    val categoryId: Long? = null,
    val categories: List<AssetCategory> = emptyList(),
    val currency: Currency = Currency.BRL,
    val institution: String = "",
    val description: String = "",
    val notes: String = "",
    val acquisitionDate: LocalDate = LocalDate.MIN,
    val investedDigits: String = "",
    val valueDigits: String = "",
    val quantity: String = "",
    val imagePath: String? = null,
    val imageFile: File? = null,
    val today: LocalDate = LocalDate.MIN,
    val nameError: Boolean = false,
    val categoryError: Boolean = false,
    val quantityError: Boolean = false,
    val done: Boolean = false,
    /** New assets: the initial investment came from cash (a transfer), in [cashCurrency] (null: the asset's). */
    val fromCash: Boolean = false,
    val cashCurrency: Currency? = null,
    val cashDigits: String? = null,
    val rates: ExchangeRates = ExchangeRates.EMPTY,
    val error: ActionError? = null,
    /** New assets only: valued "from quote" (a ticker, a quantity and a price per unit, in USD) instead of typed in. */
    val tracked: Boolean = false,
    val query: String = "",
    val matches: List<TickerMatch> = emptyList(),
    val searching: Boolean = false,
    val searchError: Boolean = false,
    val ticker: TickerMatch? = null,
    /** Price per unit as typed in [numberFormat]; tracked mode also reads [quantity] in that format. */
    val priceText: String = "",
    val preview: QuotePreview? = null,
    val previewError: Boolean = false,
    val tickerError: Boolean = false,
    val priceError: Boolean = false,
    val numberFormat: NumberFormatStyle = NumberFormatStyle.COMMA_DECIMAL,
) {
    /** Tracked mode: the typed quantity when it is a number above zero. */
    val trackedQuantity: BigDecimal? get() = DecimalInput.parse(quantity, numberFormat).getOrNull()?.takeIf { it.signum() > 0 }

    /** Tracked mode: the typed price per unit (USD) when it is a number above zero. */
    val unitPrice: BigDecimal? get() = DecimalInput.parse(priceText, numberFormat).getOrNull()?.takeIf { it.signum() > 0 }

    /** What was paid: quantity × price per unit, rounded once. */
    val cost: Money?
        get() {
            if (ticker == null) return null
            val q = trackedQuantity ?: return null
            val p = unitPrice ?: return null
            return Holdings.cost(q, p, Currency.USD)
        }

    /** The typed quantity rescaled by the splits after the purchase, up to the last close. */
    val todayQuantity: BigDecimal?
        get() {
            val p = preview ?: return null
            val q = trackedQuantity ?: return null
            return Holdings.adjust(q, p.splitsSince, acquisitionDate, p.price.date)
        }

    /** What the typed quantity is worth at the last close. */
    val todayValue: Money? get() = preview?.let { p -> todayQuantity?.let { Holdings.cost(it, p.price.price, Currency.USD) } }
}

@HiltViewModel
class AssetEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val portfolio: PortfolioRepository,
    private val categories: CategoryRepository,
    private val settings: SettingsRepository,
    private val images: ImageStore,
    rates: ExchangeRateRepository,
    time: TimeSource,
    private val prices: PriceRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val id = savedStateHandle.toRoute<AssetEditorRoute>().assetId
    private val form = MutableStateFlow(AssetEditorUiState(isNew = id == 0L, acquisitionDate = time.today(), today = time.today()))
    private var originalImage: String? = null
    private var saved = false
    private var searchJob: Job? = null
    private var previewJob: Job? = null

    /** What [pickTicker] filled in; replaced or cleared with the ticker only while the user hasn't changed it. */
    private var autoName: String? = null
    private var autoDescription: String? = null
    private var autoCategoryId: Long? = null
    private var defaultCurrency: Currency = Currency.BRL

    val state: StateFlow<AssetEditorUiState> = combine(form, categories.observeAssetCategories(includeArchived = true), rates.rates) { f, cats, r ->
        f.copy(categories = cats.filter { !it.archived || it.id == f.categoryId }, imageFile = f.imagePath?.let(images::file), rates = r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    init {
        viewModelScope.launch {
            if (id == 0L) {
                val current = settings.current()
                defaultCurrency = current.defaultCurrency
                form.update { it.copy(loading = false, currency = current.defaultCurrency, numberFormat = current.numberFormat) }
            } else {
                val asset = portfolio.asset(id) ?: return@launch form.update { it.copy(done = true) }
                originalImage = asset.imagePath
                form.update {
                    it.copy(
                        loading = false, editingTracked = asset.ticker != null, name = asset.name, categoryId = asset.categoryId, currency = asset.currency,
                        institution = asset.institution.orEmpty(), description = asset.description.orEmpty(), notes = asset.notes.orEmpty(),
                        acquisitionDate = asset.acquisitionDate, quantity = QuantityInput.format(asset.quantity), imagePath = asset.imagePath,
                    )
                }
            }
        }
    }

    fun edit(transform: (AssetEditorUiState) -> AssetEditorUiState) {
        val before = form.value
        var leftTracked = false
        form.update {
            val next = transform(it).copy(nameError = false, categoryError = false, quantityError = false, tickerError = false, priceError = false)
            leftTracked = it.tracked && !next.tracked
            // Back to "typed in": nothing of the ticker stays, and the quantity text was read in another format.
            if (leftTracked) {
                withoutTicker(next).copy(
                    query = "", matches = emptyList(), searching = false, searchError = false, quantity = "",
                    currency = if (it.ticker != null) defaultCurrency else next.currency,
                )
            } else {
                next
            }
        }
        if (leftTracked) {
            searchJob?.cancel()
            previewJob?.cancel()
            forgetAutoFill()
            return
        }
        val after = form.value
        // The splits since the purchase depend on its date.
        if (after.tracked && after.ticker != null && after.acquisitionDate != before.acquisitionDate) loadPreview(after.ticker.symbol, after.acquisitionDate)
    }

    /** Ticker search, 300 ms after the last keystroke. */
    fun onQuery(text: String) {
        form.update { it.copy(query = text, tickerError = false) }
        searchJob?.cancel()
        if (text.isBlank()) {
            form.update { it.copy(matches = emptyList(), searching = false, searchError = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            form.update { it.copy(searching = true, searchError = false) }
            val result = runCatching { prices.search(text.trim()) }
            result.exceptionOrNull()?.let { e -> if (e is CancellationException) throw e }
            // Offline is reported; an odd answer just finds nothing.
            form.update {
                it.copy(searching = false, matches = result.getOrDefault(emptyList()), searchError = result.exceptionOrNull() is IOException)
            }
        }
    }

    /** Picks [match]: USD, and name, description and category from it unless already filled in; loads its quote. */
    fun pickTicker(match: TickerMatch) {
        val name = Ticker.unitCode(match.symbol) ?: match.symbol
        val description = match.name.removeSuffix(" USD")
        form.update {
            it.copy(
                ticker = match, currency = Currency.USD, preview = null, previewError = false, tickerError = false,
                name = if (it.name.isBlank() || it.name == autoName) name else it.name,
                description = if (it.description.isBlank() || it.description == autoDescription) description else it.description,
            )
        }
        // Remembered only when used, so a name the user typed is never taken for an auto-filled one.
        autoName = name.takeIf { form.value.name == it }
        autoDescription = description.takeIf { form.value.description == it }
        loadPreview(match.symbol, form.value.acquisitionDate)
        viewModelScope.launch {
            val key = when (match.type) {
                TickerType.STOCK -> "asset.stocks"
                TickerType.ETF -> "asset.etfs"
                TickerType.CRYPTO -> "asset.cryptocurrencies"
            }
            val category = categories.observeAssetCategories(includeArchived = false).first().firstOrNull { it.seedKey == key } ?: return@launch
            var applied = false
            form.update {
                applied = it.ticker == match && (it.categoryId == null || it.categoryId == autoCategoryId)
                if (applied) it.copy(categoryId = category.id, categoryError = false) else it
            }
            if (applied) autoCategoryId = category.id
        }
    }

    fun clearTicker() {
        previewJob?.cancel()
        form.update { withoutTicker(it) }
        forgetAutoFill()
    }

    /** [s] without the ticker, its quote, and the name, description and category it filled in (unless edited since). */
    private fun withoutTicker(s: AssetEditorUiState): AssetEditorUiState = s.copy(
        ticker = null, preview = null, previewError = false,
        name = if (autoName != null && s.name == autoName) "" else s.name,
        description = if (autoDescription != null && s.description == autoDescription) "" else s.description,
        categoryId = if (autoCategoryId != null && s.categoryId == autoCategoryId) null else s.categoryId,
    )

    private fun forgetAutoFill() {
        autoName = null
        autoDescription = null
        autoCategoryId = null
    }

    private fun loadPreview(symbol: String, from: LocalDate) {
        previewJob?.cancel()
        form.update { it.copy(preview = null, previewError = false) }
        previewJob = viewModelScope.launch {
            val result = runCatching { prices.preview(symbol, from) }
            result.exceptionOrNull()?.let { e -> if (e is CancellationException) throw e }
            form.update { if (it.ticker?.symbol == symbol) it.copy(preview = result.getOrNull(), previewError = result.isFailure) else it }
        }
    }

    /** Imports the picked logo right away (picker grants are temporary); a replaced unsaved logo is deleted. */
    fun setLogo(uri: Uri) {
        viewModelScope.launch {
            val path = runCatching { images.import(uri, ImageKind.ASSET) }.getOrNull() ?: return@launch
            val previous = form.value.imagePath
            if (previous != null && previous != originalImage) images.delete(previous)
            form.update { it.copy(imagePath = path) }
        }
    }

    fun removeLogo() {
        val previous = form.value.imagePath
        if (previous != null && previous != originalImage) viewModelScope.launch { images.delete(previous) }
        form.update { it.copy(imagePath = null) }
    }

    fun save(): Boolean {
        val s = form.value
        if (s.isNew && s.tracked) return saveTracked(s)
        val quantity = QuantityInput.parse(s.quantity)
        val nameError = s.name.isBlank()
        val categoryError = s.categoryId == null
        if (nameError || categoryError || quantity.isFailure) {
            form.update { it.copy(nameError = nameError, categoryError = categoryError, quantityError = quantity.isFailure) }
            return false
        }
        val draft = AssetDraft(
            name = s.name, categoryId = s.categoryId!!, currency = s.currency, description = s.description, institution = s.institution,
            acquisitionDate = s.acquisitionDate, quantity = quantity.getOrNull(), notes = s.notes, imagePath = s.imagePath,
        )
        val invested = Money(AmountInput.minorOf(s.investedDigits) ?: 0, s.currency)
        val rates = state.value.rates
        val cash = if (s.isNew && invested.minor > 0) cashLinkAmount(s.fromCash, invested, s.acquisitionDate, s.cashCurrency ?: s.currency, s.cashDigits, rates) else null
        saved = true
        viewModelScope.launch {
            val result = runCatching {
                if (s.isNew) {
                    val value = AmountInput.minorOf(s.valueDigits)?.takeIf { s.valueDigits.isNotEmpty() }?.let { Money(it, s.currency) }
                    portfolio.createAsset(draft, invested, value, fromCash = cash)
                } else {
                    portfolio.updateAsset(id, draft)
                }
            }
            result.exceptionOrNull()?.let { e -> if (e is CancellationException) throw e }
            form.update { if (result.isSuccess) it.copy(done = true) else it.copy(error = result.exceptionOrNull()?.toActionError()) }
        }
        return true
    }

    private fun saveTracked(s: AssetEditorUiState): Boolean {
        val quantityError = s.trackedQuantity == null
        val priceError = s.unitPrice == null
        val nameError = s.name.isBlank()
        val categoryError = s.categoryId == null
        val tickerError = s.ticker == null
        val cost = s.cost
        if (tickerError || quantityError || priceError || nameError || categoryError || cost == null) {
            form.update {
                it.copy(tickerError = tickerError, quantityError = quantityError, priceError = priceError, nameError = nameError, categoryError = categoryError)
            }
            return false
        }
        val draft = AssetDraft(
            name = s.name, categoryId = s.categoryId!!, currency = Currency.USD, description = s.description, institution = s.institution,
            acquisitionDate = s.acquisitionDate, quantity = s.trackedQuantity, notes = s.notes, imagePath = s.imagePath, ticker = s.ticker!!.symbol,
        )
        val cash = cashLinkAmount(s.fromCash, cost, s.acquisitionDate, s.cashCurrency ?: Currency.USD, s.cashDigits, state.value.rates)
        saved = true
        viewModelScope.launch {
            val result = runCatching { portfolio.createAsset(draft, cost, null, fromCash = cash) }
            result.exceptionOrNull()?.let { e -> if (e is CancellationException) throw e }
            if (result.isSuccess) {
                // On the application scope: the first fetch still happens when the editor closes right away.
                appScope.launch { prices.refresh(force = true) }
                form.update { it.copy(done = true) }
            } else {
                form.update { it.copy(error = result.exceptionOrNull()?.toActionError()) }
            }
        }
        return true
    }

    fun dismissError() = form.update { it.copy(error = null) }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    override fun onCleared() {
        // A logo picked but never saved would be orphaned.
        val path = form.value.imagePath
        if (!saved && path != null && path != originalImage) images.file(path).delete()
    }
}
