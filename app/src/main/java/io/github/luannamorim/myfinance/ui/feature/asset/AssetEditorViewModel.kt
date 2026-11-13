package io.github.luannamorim.myfinance.ui.feature.asset

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luannamorim.myfinance.core.format.AmountInput
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.images.ImageKind
import io.github.luannamorim.myfinance.data.images.ImageStore
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.data.repository.AssetDraft
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.data.repository.PortfolioRepository
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.model.AssetCategory
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.ui.common.ActionError
import io.github.luannamorim.myfinance.ui.common.cashLinkAmount
import io.github.luannamorim.myfinance.ui.common.toActionError
import io.github.luannamorim.myfinance.ui.navigation.AssetEditorRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
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
)

@HiltViewModel
class AssetEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val portfolio: PortfolioRepository,
    categories: CategoryRepository,
    private val settings: SettingsRepository,
    private val images: ImageStore,
    rates: ExchangeRateRepository,
    time: TimeSource,
) : ViewModel() {

    private val id = savedStateHandle.toRoute<AssetEditorRoute>().assetId
    private val form = MutableStateFlow(AssetEditorUiState(isNew = id == 0L, acquisitionDate = time.today(), today = time.today()))
    private var originalImage: String? = null
    private var saved = false

    val state: StateFlow<AssetEditorUiState> = combine(form, categories.observeAssetCategories(includeArchived = true), rates.rates) { f, cats, r ->
        f.copy(categories = cats.filter { !it.archived || it.id == f.categoryId }, imageFile = f.imagePath?.let(images::file), rates = r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    init {
        viewModelScope.launch {
            if (id == 0L) {
                form.update { it.copy(loading = false, currency = settings.current().defaultCurrency) }
            } else {
                val asset = portfolio.asset(id) ?: return@launch form.update { it.copy(done = true) }
                originalImage = asset.imagePath
                form.update {
                    it.copy(
                        loading = false, name = asset.name, categoryId = asset.categoryId, currency = asset.currency,
                        institution = asset.institution.orEmpty(), description = asset.description.orEmpty(), notes = asset.notes.orEmpty(),
                        acquisitionDate = asset.acquisitionDate, quantity = QuantityInput.format(asset.quantity), imagePath = asset.imagePath,
                    )
                }
            }
        }
    }

    fun edit(transform: (AssetEditorUiState) -> AssetEditorUiState) =
        form.update { transform(it).copy(nameError = false, categoryError = false, quantityError = false) }

    /** Imports the picked photo right away (picker grants are temporary); a replaced unsaved photo is deleted. */
    fun setPhoto(uri: Uri) {
        viewModelScope.launch {
            val path = runCatching { images.import(uri, ImageKind.ASSET) }.getOrNull() ?: return@launch
            val previous = form.value.imagePath
            if (previous != null && previous != originalImage) images.delete(previous)
            form.update { it.copy(imagePath = path) }
        }
    }

    fun removePhoto() {
        val previous = form.value.imagePath
        if (previous != null && previous != originalImage) viewModelScope.launch { images.delete(previous) }
        form.update { it.copy(imagePath = null) }
    }

    fun save(): Boolean {
        val s = form.value
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

    fun dismissError() = form.update { it.copy(error = null) }

    override fun onCleared() {
        // A photo picked but never saved would be orphaned.
        val path = form.value.imagePath
        if (!saved && path != null && path != originalImage) images.file(path).delete()
    }
}
