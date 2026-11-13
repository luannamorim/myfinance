package io.github.luannamorim.myfinance.domain.networth

import io.github.luannamorim.myfinance.domain.cash.CashLedger
import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.model.Asset
import io.github.luannamorim.myfinance.domain.model.AssetCategory
import io.github.luannamorim.myfinance.domain.model.AssetFlow
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.Valuation
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioData
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.confirmed
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.opening
import io.github.luannamorim.myfinance.testutil.rate
import io.github.luannamorim.myfinance.testutil.usd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetWorthCalculatorTest {

    private val categories = listOf(
        AssetCategory(1, null, AssetGroup.INVESTMENT, "x", "asset.etfs", false, 0),
        AssetCategory(2, null, AssetGroup.PERSONAL, "x", "asset.vehicles", false, 0),
    )
    private val etf = Asset(10, "VOO", 1, Currency.USD, null, null, d("2026-02-02"), null, null, null)
    private val car = Asset(11, "Car", 2, Currency.BRL, null, null, d("2026-01-10"), null, null, null)
    private val portfolio = PortfolioData(
        assets = listOf(etf, car),
        flows = mapOf(
            etf.id to listOf(AssetFlow(1, etf.id, FlowKind.INITIAL, d("2026-02-02"), usd("1000.00"), null, null)),
            car.id to listOf(AssetFlow(2, car.id, FlowKind.INITIAL, d("2026-01-10"), brl("45000.00"), null, null)),
        ),
        valuations = mapOf(
            etf.id to listOf(Valuation(3, etf.id, d("2026-11-12"), usd("1100.00"), null)),
            car.id to listOf(Valuation(4, car.id, d("2026-10-01"), brl("40000.00"), null)),
        ),
        categories = categories,
    )
    private val cash = CashLedger(
        adjustments = listOf(opening(brl("10000.00"), "2026-01-01"), opening(usd("500.00"), "2026-01-01")),
        occurrences = listOf(confirmed(EntryKind.EXPENSE, brl("2000.00"), "2026-11-05")),
    )
    private val converter = Converter(ExchangeRates(listOf(rate("2026-01-02", "5.0000"), rate("2026-11-12", "5.6143"))))

    @Test
    fun netWorth_inBrl() {
        val nw = NetWorthCalculator.at(d("2026-11-12"), cash, portfolio, Currency.BRL, converter)
        // Cash: 10.000 − 2.000 + 500 × 5,6143 (2.807,15) = 10.807,15
        assertEquals(brl("10807.15"), nw.cash.money)
        // Investments: 1.100 × 5,6143 = 6.175,73
        assertEquals(brl("6175.73"), nw.investments.money)
        assertEquals(brl("40000.00"), nw.personal.money)
        assertEquals(brl("56982.88"), nw.total.money)
    }

    @Test
    fun netWorth_inUsd() {
        val nw = NetWorthCalculator.at(d("2026-11-12"), cash, portfolio, Currency.USD, converter)
        // 8.000 / 5,6143 = 1.424,93 + 500 = 1.924,93; car 40.000 / 5,6143 = 7.124,66; ETF 1.100
        assertEquals(usd("1924.93"), nw.cash.money)
        assertEquals(usd("1100.00"), nw.investments.money)
        assertEquals(usd("7124.66"), nw.personal.money)
    }

    @Test
    fun earlierDate_usesThatDatesRate_andRecords() {
        val nw = NetWorthCalculator.at(d("2026-03-31"), cash, portfolio, Currency.BRL, converter)
        // Cash 10.000 + 500 × 5,0 = 12.500; ETF carried at its net invested 1.000 × 5,0 = 5.000; car 45.000
        assertEquals(brl("12500.00"), nw.cash.money)
        assertEquals(brl("5000.00"), nw.investments.money)
        assertEquals(brl("45000.00"), nw.personal.money)
    }

    @Test
    fun missingRate_isFlagged() {
        val nw = NetWorthCalculator.at(d("2026-11-12"), cash, portfolio, Currency.BRL, Converter(ExchangeRates.EMPTY))
        assertTrue(nw.total.incomplete)
        assertEquals(brl("48000.00"), nw.total.money) // 8.000 cash + 40.000 car; USD parts missing
    }

    @Test
    fun firstRecord_isTheEarliestOfCashAndAssets() {
        assertEquals(d("2026-01-01"), NetWorthCalculator.firstRecord(cash, portfolio))
    }
}
