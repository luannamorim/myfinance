package io.github.luannamorim.myfinance.domain

import io.github.luannamorim.myfinance.domain.cash.CashLedger
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.domain.model.Asset
import io.github.luannamorim.myfinance.domain.model.AssetCategory
import io.github.luannamorim.myfinance.domain.model.AssetFlow
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind.EXPENSE
import io.github.luannamorim.myfinance.domain.model.EntryKind.INCOME
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.Valuation
import io.github.luannamorim.myfinance.domain.portfolio.Allocation
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioCalculator
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioData
import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.confirmed
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.opening
import io.github.luannamorim.myfinance.testutil.pending
import io.github.luannamorim.myfinance.testutil.rate
import io.github.luannamorim.myfinance.testutil.usd
import io.github.luannamorim.myfinance.ui.feature.overview.overviewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * One realistic month combining BRL and USD cash, recurring bills, a BRL fixed income, a USD ETF and a car.
 * Every expected figure is computed by hand in the comments (independently of the implementation).
 * Rates: 02/01/2026 = 5,0000; 12/11/2026 = 5,6143. Today = 12/11/2026.
 */
class ScenarioTest {

    private val today = d("2026-11-12")
    private val rates = ExchangeRates(listOf(rate("2026-01-02", "5.0000"), rate("2026-11-12", "5.6143")))

    private val cash = CashLedger(
        adjustments = listOf(opening(brl("10000.00"), "2026-11-01"), opening(usd("500.00"), "2026-11-01")),
        occurrences = listOf(
            confirmed(INCOME, brl("9000.00"), "2026-11-05", name = "Salary"),
            confirmed(EXPENSE, brl("2500.00"), "2026-11-10", actual = brl("2550.00"), name = "Rent"),
            pending(EXPENSE, brl("250.00"), "2026-11-20", name = "Electricity"),
            pending(EXPENSE, brl("120.00"), "2026-10-25", name = "Internet"),
            confirmed(INCOME, usd("1000.00"), "2026-11-11", name = "Freelance"),
            pending(EXPENSE, usd("20.00"), "2026-11-28", name = "Cloud"),
        ),
    )

    private val categories = listOf(
        AssetCategory(1, null, AssetGroup.INVESTMENT, "receipt_long", "asset.fixed_income", false, 0),
        AssetCategory(2, null, AssetGroup.INVESTMENT, "stacked_line_chart", "asset.etfs", false, 1),
        AssetCategory(3, null, AssetGroup.PERSONAL, "directions_car", "asset.vehicles", false, 0),
    )
    private val tesouro = Asset(10, "Tesouro Selic", 1, Currency.BRL, null, null, d("2026-01-10"), null, null, null)
    private val etf = Asset(11, "VOO", 2, Currency.USD, null, null, d("2026-01-15"), null, null, null)
    private val car = Asset(12, "Car", 3, Currency.BRL, null, null, d("2026-01-10"), null, null, null)
    private val portfolio = PortfolioData(
        assets = listOf(tesouro, etf, car),
        flows = mapOf(
            tesouro.id to listOf(AssetFlow(1, tesouro.id, FlowKind.INITIAL, d("2026-01-10"), brl("5000.00"), null, null)),
            etf.id to listOf(AssetFlow(2, etf.id, FlowKind.INITIAL, d("2026-01-15"), usd("1000.00"), null, null)),
            car.id to listOf(AssetFlow(3, car.id, FlowKind.INITIAL, d("2026-01-10"), brl("45000.00"), null, null)),
        ),
        valuations = mapOf(
            tesouro.id to listOf(Valuation(4, tesouro.id, d("2026-03-31"), brl("5120.00"), null)),
            etf.id to listOf(Valuation(5, etf.id, d("2026-11-12"), usd("1100.00"), null)),
            car.id to listOf(Valuation(6, car.id, d("2026-10-01"), brl("40000.00"), null)),
        ),
        categories = categories,
    )

    @Test
    fun cash_inBrl() {
        val s = overviewState(cash, portfolio, emptyList(), rates, Currency.BRL, today)
        // BRL: 10.000 + 9.000 − 2.550 = 16.450; USD: 500 + 1.000 = 1.500 → 1.500 × 5,6143 = 8.421,45
        assertEquals(Total(brl("24871.45")), s.available)
        // BRL 16.450 − 250 − 120 = 16.080; USD 1.500 − 20 = 1.480 × 5,6143 = 8.309,16 → 24.389,16
        assertEquals(Total(brl("24389.16")), s.projected)
        val m = s.month!!
        // Freelance received 11/11 converts at the last rate on or before it (02/01: 5,0) = 5.000
        assertEquals(Total(brl("14000.00")), m.expectedIncome)
        assertEquals(Total(brl("14000.00")), m.receivedIncome)
        // 2.550 + 250 + 20 × 5,6143 (112,286) = 2.912,29 (rounded once)
        assertEquals(Total(brl("2912.29")), m.expectedExpenses)
        assertEquals(Total(brl("2550.00")), m.paidExpenses)
        assertEquals(Total(brl("120.00")), m.overdueEarlier)
        assertEquals(listOf("Internet", "Electricity", "Cloud"), s.upcoming.map { it.name })
        assertEquals(listOf("Freelance", "Rent", "Salary"), s.recent.map { it.name })
        assertTrue(s.usesConversion)
    }

    @Test
    fun portfolio_inBrl() {
        val converter = Converter(rates)
        val t = PortfolioCalculator.totals(portfolio, today, Currency.BRL, converter)
        // 5.120 + 1.100 × 5,6143 (6.175,73) + 40.000 = 51.295,73
        assertEquals(Total(brl("51295.73")), t.value)
        // 5.000 + 1.000 × 5,0 + 45.000 = 55.000
        assertEquals(Total(brl("55000.00")), t.invested)
        assertEquals(Total(brl("-3704.27")), t.gain)
        assertEquals(BigDecimal("-0.0673503636"), t.simpleReturn) // −3.704,27 / 55.000
        val shares = Allocation.of(portfolio, today, Currency.BRL, converter).map { it.categoryId to it.share }
        assertEquals(listOf(3L to BigDecimal("0.7798"), 2L to BigDecimal("0.1204"), 1L to BigDecimal("0.0998")), shares)
    }

    @Test
    fun netWorth_inBrlAndUsd() {
        val brlView = overviewState(cash, portfolio, emptyList(), rates, Currency.BRL, today).netWorth!!
        // 24.871,45 + 11.295,73 + 40.000 = 76.167,18
        assertEquals(brl("24871.45"), brlView.cash.money)
        assertEquals(brl("11295.73"), brlView.investments.money)
        assertEquals(brl("40000.00"), brlView.personal.money)
        assertEquals(brl("76167.18"), brlView.total.money)

        val usdView = overviewState(cash, portfolio, emptyList(), rates, Currency.USD, today).netWorth!!
        // Cash 16.450 / 5,6143 + 1.500 = 4.430,02; investments 5.120 / 5,6143 + 1.100 = 2.011,96; car 40.000 / 5,6143 = 7.124,66
        assertEquals(usd("4430.02"), usdView.cash.money)
        assertEquals(usd("2011.96"), usdView.investments.money)
        assertEquals(usd("7124.66"), usdView.personal.money)
        assertEquals(usd("13566.64"), usdView.total.money)
    }
}
