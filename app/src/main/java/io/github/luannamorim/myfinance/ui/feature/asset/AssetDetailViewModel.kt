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
import io.github.luannamorim.myfinance.domain.market.Split
import io.github.luannamorim.myfinance.domain.model.Asset
import io.github.luannamorim.myfinance.domain.model.AssetCategory
import io.github.luannamorim.myfinance.domain.model.AssetFlow
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.portfolio.AssetSnapshot
import io.github.luannamorim.myfinance.domain.portfolio.ConvertedSnapshot
import io.github.luannamorim.myfinance.domain.portfolio.Holdings
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
import java.time.ZoneId
import javax.inject.Inject

/** CONTRIBUTE and WITHDRAW are "Buy" and "Sell" for a tracked asset. */
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
    val today: LocalDate = LocalDate.MIN,
    val sheet: AssetSheet? = null,
    val gone: Boolean = false,
    val rates: ExchangeRates = ExchangeRates.EMPTY,
    val error: ActionError? = null,
    /** A quoted asset ([Asset.ticker]): buys and sells by quantity, no valuations. */
    val tracked: Boolean = false,
    /** Shares or coins held today (tracked assets); null when unknown. */
    val held: BigDecimal? = null,
    /** Splits of the ticker, to know what was held on a past date. */
    val splits: List<Split> = emptyList(),
    /** The device zone, for the time of today's quote. */
    val zone: ZoneId = ZoneId.systemDefault(),
    /** What the asset's done transfers changed in cash, per currency (negative: it left cash); empty without any. */
    val cashMoved: List<Money> = emptyList(),
) {
    val closed: Boolean get() = snapshot?.closed == true
    val valuationDates: Set<LocalDate> get() = valuations.map { it.valuation.date }.toSet()

    /** Shares or coins held at the end of [date]; [held] when the flows don't say. */
    fun heldOn(date: LocalDate): BigDecimal = Holdings.quantityAt(flows, splits, date) ?: held ?: BigDecimal.ZERO
}

fun assetDetailState(
    data: PortfolioData,
    assetId: Long,
    rates: ExchangeRates,
    display: Currency,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    imageFile: (String) -> File?,
): AssetDetailUiState {
    val asset = data.assets.firstOrNull { it.id == assetId } ?: return AssetDetailUiState(loading = false, gone = true, today = today, zone = zone)
    val flows = data.flowsOf(asset)
    val valuations = data.valuationsOf(asset)
    val converter = Converter(rates)
    val snapshot = PortfolioCalculator.snapshot(data, asset, today)
    val single = data.only(asset)
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
        today = today,
        rates = rates,
        tracked = asset.ticker != null,
        held = snapshot?.quantity,
        splits = asset.ticker?.let(data.market::splitsOf).orEmpty(),
        zone = zone,
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

    private val portfolioAndCash = combine(portfolio.data, portfolio.cashMovedBy(id)) { data, moved -> data to moved }

    val state: StateFlow<AssetDetailUiState> = combine(portfolioAndCash, rates.rates, settings.settings, sheet, error) { (data, moved), r, s, open, e ->
        assetDetailState(data, id, r, s.displayCurrency, time.today(), time.zone(), images::file).copy(sheet = open, error = e, cashMoved = moved)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetDetailUiState())

    fun open(value: AssetSheet?) {
        sheet.value = value
    }

    fun addValuation(date: LocalDate, value: Money, note: String?) {
        sheet.value = null
        perform { portfolio.addValuation(id, date, value, note) }
    }

    /**
     * [cash]: what left cash (a contribution) or entered it (a withdrawal), recorded as a transfer. A tracked asset
     * passes the quantity bought or sold (null for selling everything) and no [newValue].
     */
    fun addFlow(kind: FlowKind, date: LocalDate, amount: Money, quantity: BigDecimal?, note: String?, newValue: Money?, cash: Money?) {
        sheet.update { null }
        perform { portfolio.addFlow(id, kind, date, amount, quantity, note, newValue, cash) }
    }

    fun deleteFlow(flowId: Long) = perform { portfolio.deleteFlow(flowId) }
    fun deleteValuation(valuationId: Long) = perform { portfolio.deleteValuation(valuationId) }
    /** [undoTransfers]: the asset's done transfers go too, and cash gets back what they moved. */
    fun deleteAsset(undoTransfers: Boolean) = perform { portfolio.deleteAsset(id, undoTransfers) }
    fun dismissError() = error.update { null }

    /** Runs a write; a refusal becomes a dialog instead of a crash. */
    private fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }.onFailure { e -> if (e is CancellationException) throw e else error.update { e.toActionError() } }
        }
    }
}
