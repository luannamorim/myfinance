package io.github.luannamorim.myfinance.ui.feature.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.data.repository.PortfolioRepository
import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.domain.model.AssetCategory
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.ExchangeRate
import io.github.luannamorim.myfinance.domain.portfolio.Allocation
import io.github.luannamorim.myfinance.domain.portfolio.AssetSnapshot
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioCalculator
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioData
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioTotals
import io.github.luannamorim.myfinance.domain.portfolio.Slice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

/** An asset line: its own figures, and its value in the display currency when different. */
data class AssetRow(val snapshot: AssetSnapshot, val category: AssetCategory?, val displayValue: Total?)

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
    val rate: ExchangeRate? = null,
    val usesConversion: Boolean = false,
    val empty: Boolean = true,
)

fun portfolioState(data: PortfolioData, rates: ExchangeRates, display: Currency, group: AssetGroup?, selected: Long?, today: LocalDate): PortfolioUiState {
    val converter = Converter(rates)
    val categories = data.categories.associateBy { it.id }
    val rows = data.assets.filter { group == null || data.groupOf(it) == group }.mapNotNull { asset ->
        val flows = data.flowsOf(asset)
        val s = PortfolioCalculator.snapshot(asset, flows, data.valuationsOf(asset), today) ?: return@mapNotNull null
        AssetRow(s, categories[asset.categoryId], if (asset.currency != display) converter.sum(listOf(s.value to today), display) else null)
    }
    val slices = Allocation.of(data, today, display, converter, group)
    return PortfolioUiState(
        loading = false,
        group = group,
        selectedCategory = selected?.takeIf { id -> slices.any { it.categoryId == id } },
        display = display,
        totals = PortfolioCalculator.totals(data, today, display, converter, group),
        slices = slices.map { it to categories[it.categoryId] },
        assets = rows.filter { !it.snapshot.closed && (selected == null || it.snapshot.asset.categoryId == selected) }
            .sortedByDescending { (it.displayValue?.money ?: it.snapshot.value).minor },
        closed = rows.filter { it.snapshot.closed },
        rate = rates.latest(),
        usesConversion = data.assets.any { it.currency != display },
        empty = data.assets.isEmpty(),
    )
}

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    portfolio: PortfolioRepository,
    rates: ExchangeRateRepository,
    settings: SettingsRepository,
    private val time: TimeSource,
) : ViewModel() {

    private val filter = MutableStateFlow<Pair<AssetGroup?, Long?>>(null to null)

    val state: StateFlow<PortfolioUiState> = combine(portfolio.data, rates.rates, settings.settings, filter) { data, r, s, f ->
        portfolioState(data, r, s.displayCurrency, f.first, f.second, time.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortfolioUiState())

    fun setGroup(group: AssetGroup?) {
        filter.value = group to null
    }

    fun select(categoryId: Long?) = filter.update { it.first to categoryId }
}
