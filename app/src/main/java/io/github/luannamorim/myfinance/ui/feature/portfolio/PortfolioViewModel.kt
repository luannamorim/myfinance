package io.github.luannamorim.myfinance.ui.feature.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.images.ImageStore
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.data.prefs.PriceError
import io.github.luannamorim.myfinance.data.prefs.RateError
import io.github.luannamorim.myfinance.data.repository.PortfolioRepository
import io.github.luannamorim.myfinance.data.repository.PriceRefreshResult
import io.github.luannamorim.myfinance.data.repository.PriceRepository
import io.github.luannamorim.myfinance.data.repository.RefreshResult
import io.github.luannamorim.myfinance.domain.cash.CashCalculator
import io.github.luannamorim.myfinance.domain.cash.CashLedger
import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.domain.model.AssetCategory
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.domain.portfolio.Allocation
import io.github.luannamorim.myfinance.domain.portfolio.AssetSnapshot
import io.github.luannamorim.myfinance.domain.portfolio.ConvertedSnapshot
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioCalculator
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioData
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioTotals
import io.github.luannamorim.myfinance.domain.portfolio.Slice
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject

/** An asset line: its own figures, its figures in the display currency when different, and its logo. */
data class AssetRow(val snapshot: AssetSnapshot, val category: AssetCategory?, val converted: ConvertedSnapshot?, val logo: File? = null) {
    val displayValue: Total? get() = converted?.value
}

data class PortfolioUiState(
    val loading: Boolean = true,
    val group: AssetGroup? = null,
    val selectedCategory: Long? = null,
    val display: Currency = Currency.BRL,
    val totals: PortfolioTotals? = null,
    val slices: List<Pair<Slice, AssetCategory?>> = emptyList(),
    /** Open assets (filtered by group and selected category), largest value first. */
    val assets: List<AssetRow> = emptyList(),
    val closed: List<AssetRow> = emptyList(),
    val empty: Boolean = true,
    /** Cash available today, from Cash Flow: a reference shown in "All", never part of the portfolio figures. */
    val cash: Total? = null,
    /** Cash's part of cash + portfolio when cash is positive (the donut then shows it as a slice). */
    val cashShare: BigDecimal? = null,
    /** An "Update" run (exchange rates and quotes) is in progress. */
    val refreshing: Boolean = false,
)

/** What an "Update" run on the Portfolio reports: the first failure (rates before quotes), else whether anything changed. */
sealed interface PortfolioRefresh {
    data object Updated : PortfolioRefresh
    data object UpToDate : PortfolioRefresh
    data class RatesFailed(val reason: RateError) : PortfolioRefresh
    data class PricesFailed(val reason: PriceError) : PortfolioRefresh
}

fun portfolioRefresh(rates: RefreshResult, prices: PriceRefreshResult): PortfolioRefresh = when {
    rates is RefreshResult.Failed -> PortfolioRefresh.RatesFailed(rates.reason)
    prices is PriceRefreshResult.Failed -> PortfolioRefresh.PricesFailed(prices.reason)
    rates is RefreshResult.Updated || prices is PriceRefreshResult.Updated -> PortfolioRefresh.Updated
    else -> PortfolioRefresh.UpToDate
}

fun portfolioState(
    data: PortfolioData,
    rates: ExchangeRates,
    display: Currency,
    group: AssetGroup?,
    selected: Long?,
    today: LocalDate,
    cash: CashLedger? = null,
    logoFile: (String) -> File? = { null },
): PortfolioUiState {
    val converter = Converter(rates)
    val categories = data.categories.associateBy { it.id }
    val rows = data.assets.filter { group == null || data.groupOf(it) == group }.mapNotNull { asset ->
        val flows = data.flowsOf(asset)
        val s = PortfolioCalculator.snapshot(data, asset, today) ?: return@mapNotNull null
        AssetRow(
            s, categories[asset.categoryId], if (asset.currency != display) PortfolioCalculator.convertedSnapshot(s, flows, today, display, converter) else null,
            asset.imagePath?.let(logoFile),
        )
    }
    val slices = Allocation.of(data, today, display, converter, group)
    val totals = PortfolioCalculator.totals(data, today, display, converter, group)
    // Cash is shown only in "All", and only once Cash Flow has something (an opening balance or a done entry).
    val usesCash = cash != null && (cash.adjustments.isNotEmpty() || cash.occurrences.any { it.state == OccurrenceState.CONFIRMED })
    val cashTotal = if (group == null && usesCash) CashCalculator.consolidate(CashCalculator.available(cash!!, today), display, converter) else null
    val cashShare = cashTotal?.money?.minor?.takeIf { it > 0 }?.let { c ->
        val whole = c + (totals?.value?.money?.minor ?: 0)
        BigDecimal(c).divide(BigDecimal(whole), 4, RoundingMode.HALF_EVEN)
    }
    return PortfolioUiState(
        loading = false,
        group = group,
        selectedCategory = selected?.takeIf { id -> slices.any { it.categoryId == id } },
        display = display,
        totals = totals,
        slices = slices.map { it to categories[it.categoryId] },
        assets = rows.filter { !it.snapshot.closed && (selected == null || it.snapshot.asset.categoryId == selected) }
            .sortedByDescending { (it.displayValue?.money ?: it.snapshot.value).minor },
        closed = rows.filter { it.snapshot.closed },
        empty = data.assets.isEmpty(),
        cash = cashTotal,
        cashShare = cashShare,
    )
}

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    portfolio: PortfolioRepository,
    cash: CashRepository,
    private val rates: ExchangeRateRepository,
    private val prices: PriceRepository,
    settings: SettingsRepository,
    private val images: ImageStore,
    private val time: TimeSource,
) : ViewModel() {

    private val filter = MutableStateFlow<Pair<AssetGroup?, Long?>>(null to null)
    private val refreshing = MutableStateFlow(false)
    private val _refreshResult = MutableStateFlow<PortfolioRefresh?>(null)

    /** The last "Update" run's outcome, until shown ([refreshShown]). */
    val refreshResult: StateFlow<PortfolioRefresh?> = _refreshResult

    val state: StateFlow<PortfolioUiState> = combine(portfolio.data, cash.ledger, rates.rates, settings.settings, filter) { data, ledger, r, s, f ->
        portfolioState(data, r, s.displayCurrency, f.first, f.second, time.today(), ledger, images::file)
    }.combine(refreshing) { s, r -> s.copy(refreshing = r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortfolioUiState())

    /**
     * Fetches exchange rates and quotes now (ignoring the automatic throttles and switches, like "Update" in
     * Settings). Ignored while a run is in progress, so repeated taps send no extra requests.
     */
    fun refresh() {
        if (!refreshing.compareAndSet(expect = false, update = true)) return
        _refreshResult.value = null
        viewModelScope.launch {
            try {
                val r = async { rates.refresh(force = true) }
                val p = async { prices.refresh(force = true) }
                _refreshResult.value = portfolioRefresh(r.await(), p.await())
            } finally {
                refreshing.value = false
            }
        }
    }

    fun refreshShown() {
        _refreshResult.value = null
    }

    fun setGroup(group: AssetGroup?) {
        filter.value = group to null
    }

    fun select(categoryId: Long?) = filter.update { it.first to categoryId }
}
