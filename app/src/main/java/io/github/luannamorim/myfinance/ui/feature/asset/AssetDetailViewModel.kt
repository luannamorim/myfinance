package io.github.luannamorim.myfinance.ui.feature.asset

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.images.ImageStore
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.data.repository.PortfolioRepository
import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.model.Asset
import io.github.luannamorim.myfinance.domain.model.AssetCategory
import io.github.luannamorim.myfinance.domain.model.AssetFlow
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.ExchangeRate
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.portfolio.AssetSnapshot
import io.github.luannamorim.myfinance.domain.portfolio.ConvertedSnapshot
import io.github.luannamorim.myfinance.domain.portfolio.Period
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioCalculator
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioData
import io.github.luannamorim.myfinance.domain.portfolio.Series
import io.github.luannamorim.myfinance.domain.portfolio.SeriesPoint
import io.github.luannamorim.myfinance.domain.portfolio.ValuationRow
import io.github.luannamorim.myfinance.ui.common.ActionError
import io.github.luannamorim.myfinance.ui.common.toActionError
import io.github.luannamorim.myfinance.ui.navigation.AssetDetailRoute
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

enum class AssetSheet { VALUATION, CONTRIBUTE, WITHDRAW }

data class AssetDetailUiState(
    val loading: Boolean = true,
    val asset: Asset? = null,
    val category: AssetCategory? = null,
    val snapshot: AssetSnapshot? = null,
    /** The same figures in the display currency, when it differs from the asset's. */
    val converted: ConvertedSnapshot? = null,
    val display: Currency = Currency.BRL,
    val valuations: List<ValuationRow> = emptyList(),
    val flows: List<AssetFlow> = emptyList(),
    /** Invested vs value over time, in the asset currency. */
    val series: List<SeriesPoint> = emptyList(),
    val imageFile: File? = null,
    val rate: ExchangeRate? = null,
    val today: LocalDate = LocalDate.MIN,
    val sheet: AssetSheet? = null,
    val gone: Boolean = false,
    val rates: ExchangeRates = ExchangeRates.EMPTY,
    val error: ActionError? = null,
) {
    val closed: Boolean get() = snapshot?.closed == true
    val valuationDates: Set<LocalDate> get() = valuations.map { it.valuation.date }.toSet()
}

fun assetDetailState(data: PortfolioData, assetId: Long, rates: ExchangeRates, display: Currency, today: LocalDate, imageFile: (String) -> File?): AssetDetailUiState {
    val asset = data.assets.firstOrNull { it.id == assetId } ?: return AssetDetailUiState(loading = false, gone = true, today = today)
    val flows = data.flowsOf(asset)
    val valuations = data.valuationsOf(asset)
    val converter = Converter(rates)
    val snapshot = PortfolioCalculator.snapshot(asset, flows, valuations, today)
    val single = PortfolioData(listOf(asset), mapOf(asset.id to flows), mapOf(asset.id to valuations), data.categories)
    return AssetDetailUiState(
        loading = false,
        asset = asset,
        category = data.categories.firstOrNull { it.id == asset.categoryId },
        snapshot = snapshot,
        converted = if (snapshot != null && asset.currency != display) PortfolioCalculator.convertedSnapshot(snapshot, flows, today, display, converter) else null,
        display = display,
        valuations = PortfolioCalculator.valuationRows(asset, flows, valuations).reversed(),
        flows = flows.sortedWith(compareByDescending<AssetFlow> { it.date }.thenByDescending { it.id }),
        series = Series.portfolio(single, Period.ALL, today, asset.currency, converter),
        imageFile = asset.imagePath?.let(imageFile),
        rate = rates.latest(),
        today = today,
        rates = rates,
    )
}

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val portfolio: PortfolioRepository,
    rates: ExchangeRateRepository,
    settings: SettingsRepository,
    private val images: ImageStore,
    private val time: TimeSource,
) : ViewModel() {

    private val id = savedStateHandle.toRoute<AssetDetailRoute>().assetId
    private val sheet = MutableStateFlow<AssetSheet?>(null)
    private val error = MutableStateFlow<ActionError?>(null)

    val state: StateFlow<AssetDetailUiState> = combine(portfolio.data, rates.rates, settings.settings, sheet, error) { data, r, s, open, e ->
        assetDetailState(data, id, r, s.displayCurrency, time.today(), images::file).copy(sheet = open, error = e)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetDetailUiState())

    fun open(value: AssetSheet?) {
        sheet.value = value
    }

    fun addValuation(date: LocalDate, value: Money, note: String?) {
        sheet.value = null
        perform { portfolio.addValuation(id, date, value, note) }
    }

    /** [cash]: what left cash (a contribution) or entered it (a withdrawal), recorded as a transfer. */
    fun addFlow(kind: FlowKind, date: LocalDate, amount: Money, quantity: BigDecimal?, note: String?, newValue: Money?, cash: Money?) {
        sheet.update { null }
        perform { portfolio.addFlow(id, kind, date, amount, quantity, note, newValue, cash) }
    }

    fun deleteFlow(flowId: Long) = perform { portfolio.deleteFlow(flowId) }
    fun deleteValuation(valuationId: Long) = perform { portfolio.deleteValuation(valuationId) }
    fun deleteAsset() = perform { portfolio.deleteAsset(id) }
    fun dismissError() = error.update { null }

    /** Runs a write; a refusal becomes a dialog instead of a crash. */
    private fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }.onFailure { e -> if (e is CancellationException) throw e else error.update { e.toActionError() } }
        }
    }
}
