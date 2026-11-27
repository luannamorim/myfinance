package io.github.luannamorim.myfinance.ui.feature.portfolio

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.luannamorim.myfinance.data.prefs.PriceError
import io.github.luannamorim.myfinance.data.prefs.RateError
import io.github.luannamorim.myfinance.data.repository.PriceRefreshResult
import io.github.luannamorim.myfinance.data.repository.RefreshResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.model.Asset
import io.github.luannamorim.myfinance.domain.model.AssetCategory
import io.github.luannamorim.myfinance.domain.model.AssetFlow
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.Valuation
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioData
import io.github.luannamorim.myfinance.testutil.PHONE_TALL
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.rate
import io.github.luannamorim.myfinance.testutil.snapBothThemes
import io.github.luannamorim.myfinance.testutil.usd
import io.github.luannamorim.myfinance.ui.components.DonutSlice
import io.github.luannamorim.myfinance.ui.components.hitSlice
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = PHONE_TALL)
class PortfolioTest {

    @get:Rule val compose = createComposeRule()

    private val today = d("2026-11-12")
    private val categories = listOf(
        AssetCategory(1, null, AssetGroup.INVESTMENT, "receipt_long", "asset.fixed_income", false, 0),
        AssetCategory(2, null, AssetGroup.INVESTMENT, "stacked_line_chart", "asset.etfs", false, 1),
        AssetCategory(3, null, AssetGroup.PERSONAL, "directions_car", "asset.vehicles", false, 2),
    )
    private var ids = 100L
    private fun asset(name: String, category: Long, currency: Currency = Currency.BRL) = Asset(ids++, name, category, currency, null, null, d("2026-01-10"), null, null, null)
    private fun flow(a: Asset, amount: Money, on: String, kind: FlowKind = FlowKind.INITIAL) = AssetFlow(ids++, a.id, kind, d(on), amount, null, null)
    private fun valuation(a: Asset, value: Money, on: String) = Valuation(ids++, a.id, d(on), value, null)

    private val tesouro = asset("Tesouro Selic", 1)
    private val voo = asset("VOO", 2, Currency.USD)
    private val car = asset("Car", 3)
    private val old = asset("Old CDB", 1)
    private val data = PortfolioData(
        assets = listOf(tesouro, voo, car, old),
        flows = mapOf(
            tesouro.id to listOf(flow(tesouro, brl("5000.00"), "2026-01-10")),
            voo.id to listOf(flow(voo, usd("1000.00"), "2026-02-02")),
            car.id to listOf(flow(car, brl("0.00"), "2026-01-10")),
            old.id to listOf(flow(old, brl("1000.00"), "2026-01-10"), flow(old, brl("1080.00"), "2026-06-01", FlowKind.FULL_DISPOSAL)),
        ),
        valuations = mapOf(
            tesouro.id to listOf(valuation(tesouro, brl("5120.00"), "2026-03-31")),
            voo.id to listOf(valuation(voo, usd("1100.00"), "2026-11-12")),
            car.id to listOf(valuation(car, brl("40000.00"), "2026-01-10")),
        ),
        categories = categories,
    )
    private val rates = ExchangeRates(listOf(rate("2026-02-02", "5.0000"), rate("2026-11-12", "5.6143")))

    @Test
    fun hitSlice_clockwiseFromTwelve() {
        val slices = listOf(DonutSlice(1, 0.5f, Color.White, "A"), DonutSlice(2, 0.25f, Color.White, "B"), DonutSlice(3, 0.25f, Color.White, "C"))
        assertEquals(1L, hitSlice(slices, 10f))
        assertEquals(2L, hitSlice(slices, 200f))
        assertEquals(3L, hitSlice(slices, 300f))
        assertEquals(1L, hitSlice(slices, 370f))
        assertNull(hitSlice(emptyList(), 10f))
    }

    @Test
    fun state_totalsAllocationAndClosed() {
        val s = portfolioState(data, rates, Currency.BRL, null, null, today)
        // 5.120 + 1.100 × 5,6143 (6.175,73) + 40.000 = 51.295,73
        assertEquals(brl("51295.73"), s.totals!!.value.money)
        assertEquals(brl("80.00"), s.totals!!.realizedClosed.money)
        // Gain ÷ invested, the car included: (120 + 1.175,73 + 40.000) / (5.000 + 5.000 + 0) = 412,9573%
        assertEquals(java.math.BigDecimal("4.1295730000"), s.totals!!.simpleReturn)
        assertEquals(listOf(3L, 2L, 1L), s.slices.map { it.first.categoryId })
        assertEquals(listOf("Car", "VOO", "Tesouro Selic"), s.assets.map { it.snapshot.asset.name })
        assertEquals(listOf("Old CDB"), s.closed.map { it.snapshot.asset.name })
    }

    @Test
    fun rows_carryTheLogo() {
        val withLogo = data.copy(assets = data.assets.map { if (it.id == car.id) it.copy(imagePath = "images/assets/car.png") else it })
        val s = portfolioState(withLogo, rates, Currency.BRL, null, null, today, logoFile = { java.io.File("/x", it) })
        assertEquals(java.io.File("/x/images/assets/car.png"), s.assets.single { it.snapshot.asset.id == car.id }.logo)
        assertNull(s.assets.single { it.snapshot.asset.id == tesouro.id }.logo)
    }

    @Test
    fun foreignRow_showsGainInDisplayCurrency_andTotalsHaveNoExchangeLine() {
        val state = portfolioState(data, rates, Currency.BRL, null, null, today)
        val row = state.assets.single { it.snapshot.asset.id == voo.id }
        // VOO: US$ 1.000 at 5,00 is now US$ 1.100 at 5,6143: R$ 1.175,73 (÷ 5.000 = +23,51%), R$ 614,30 of it from the dollar.
        assertEquals(brl("1175.73"), row.converted!!.gain.money)
        assertNull(state.assets.single { it.snapshot.asset.id == tesouro.id }.converted)
        compose.setContent { FinanceTheme { PortfolioContent(state, PortfolioActions()) } }
        compose.onNodeWithText("+R$ 1.175,73").assertExists()
        compose.onNodeWithText(" · +23,51%").assertExists()
        compose.onNodeWithText("+US$ 100,00").assertDoesNotExist()
        compose.onNodeWithText("From the exchange rate").assertDoesNotExist()
        compose.onNodeWithText("+R$ 614,30").assertDoesNotExist()
    }

    @Test
    fun refreshOutcome_firstFailureThenAnyUpdate() {
        val ratesDown = RefreshResult.Failed(RateError.NETWORK)
        val pricesDown = PriceRefreshResult.Failed(PriceError.NOT_FOUND)
        assertEquals(PortfolioRefresh.RatesFailed(RateError.NETWORK), portfolioRefresh(ratesDown, pricesDown))
        assertEquals(PortfolioRefresh.PricesFailed(PriceError.NOT_FOUND), portfolioRefresh(RefreshResult.Updated(2), pricesDown))
        assertEquals(PortfolioRefresh.Updated, portfolioRefresh(RefreshResult.UpToDate, PriceRefreshResult.Updated(3)))
        assertEquals(PortfolioRefresh.Updated, portfolioRefresh(RefreshResult.Updated(1), PriceRefreshResult.NothingTracked))
        assertEquals(PortfolioRefresh.UpToDate, portfolioRefresh(RefreshResult.UpToDate, PriceRefreshResult.NothingTracked))
    }

    @Test
    fun refreshButton_callsBack_andWaitsWhileRefreshing() {
        var taps = 0
        val state = mutableStateOf(portfolioState(data, rates, Currency.BRL, null, null, today))
        compose.setContent { FinanceTheme { PortfolioContent(state.value, PortfolioActions(onRefresh = { taps++ })) } }
        compose.onNodeWithContentDescription("Update rates and quotes").assertIsEnabled().performClick()
        assertEquals(1, taps)
        state.value = state.value.copy(refreshing = true)
        compose.onNodeWithContentDescription("Update rates and quotes").assertIsNotEnabled()
        state.value = portfolioState(PortfolioData(categories = categories), rates, Currency.BRL, null, null, today)
        compose.onNodeWithContentDescription("Update rates and quotes").assertDoesNotExist()
    }

    @Test
    fun groupAndCategoryFilters() {
        val investments = portfolioState(data, rates, Currency.BRL, AssetGroup.INVESTMENT, null, today)
        assertEquals(listOf("VOO", "Tesouro Selic"), investments.assets.map { it.snapshot.asset.name })
        assertFalse(investments.slices.any { it.first.categoryId == 3L })
        val selected = portfolioState(data, rates, Currency.BRL, null, 1L, today)
        assertEquals(listOf("Tesouro Selic"), selected.assets.map { it.snapshot.asset.name })
        assertEquals(1L, selected.selectedCategory)
    }

    @Test
    fun screenshots() {
        // With Cash Flow in use, "All" shows cash for reference (a slice and a card).
        val cash = io.github.luannamorim.myfinance.domain.cash.CashLedger(adjustments = listOf(io.github.luannamorim.myfinance.testutil.opening(brl("18944.75"), "2026-11-01")))
        // The car bought for 45.000 (the README shows this one): an inherited car would add its 40.000 to the gain
        // with nothing invested. (120 + 1.175,73 − 5.000) ÷ 55.000 = −6,74%.
        val bought = data.copy(flows = data.flows + (car.id to listOf(flow(car, brl("45000.00"), "2026-01-10"))))
        compose.snapBothThemes("portfolio") { PortfolioContent(portfolioState(bought, rates, Currency.BRL, null, null, today, cash), PortfolioActions()) }
    }

    @Test
    fun screenshot_logo() {
        val file = java.io.File(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "logo.png")
        val bitmap = android.graphics.Bitmap.createBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).drawCircle(60f, 60f, 50f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(27, 42, 74) })
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val withLogo = data.copy(assets = data.assets.map { if (it.id == tesouro.id) it.copy(imagePath = "logo") else it })
        compose.snapBothThemes("portfolio_logo") { PortfolioContent(portfolioState(withLogo, rates, Currency.BRL, null, null, today, logoFile = { file }), PortfolioActions()) }
    }

    @Test
    fun screenshot_selected() {
        compose.snapBothThemes("portfolio_selected") { PortfolioContent(portfolioState(data, rates, Currency.BRL, null, 2L, today), PortfolioActions()) }
    }

    @Test
    fun charts_periodGainAndSeries() {
        val cash = io.github.luannamorim.myfinance.domain.cash.CashLedger(adjustments = listOf(io.github.luannamorim.myfinance.testutil.opening(brl("10000.00"), "2026-01-01")))
        val charts = portfolioCharts(data, cash, rates, Currency.BRL, io.github.luannamorim.myfinance.domain.portfolio.Period.ALL, today)
        assertEquals(today, charts.netWorth.last().first)
        assertEquals(d("2026-01-31"), charts.netWorth.first().first)
        // All time: gains of every asset: Tesouro 120 + VOO 1.175,73 + car 40.000 + Old CDB 80 = 41.375,73
        assertEquals(brl("41375.73"), charts.periodGain!!.money)
    }

    @Test
    fun screenshot_charts() {
        val cash = io.github.luannamorim.myfinance.domain.cash.CashLedger(adjustments = listOf(io.github.luannamorim.myfinance.testutil.opening(brl("10000.00"), "2026-01-01")))
        compose.snapBothThemes("portfolio_charts") {
            PortfolioChartsContent(portfolioCharts(data, cash, rates, Currency.BRL, io.github.luannamorim.myfinance.domain.portfolio.Period.Y1, today), {}, {})
        }
    }
}
