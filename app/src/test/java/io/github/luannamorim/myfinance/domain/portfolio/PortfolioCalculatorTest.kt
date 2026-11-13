package io.github.luannamorim.myfinance.domain.portfolio

import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.domain.model.Asset
import io.github.luannamorim.myfinance.domain.model.AssetCategory
import io.github.luannamorim.myfinance.domain.model.AssetFlow
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.Valuation
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.rate
import io.github.luannamorim.myfinance.testutil.usd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Expected values computed by hand in the comments. */
class PortfolioCalculatorTest {

    private var ids = 1L
    private fun asset(currency: Currency = Currency.BRL, category: Long = 1) =
        Asset(ids++, "Asset", category, currency, null, null, d("2026-01-10"), null, null, null)
    private fun flow(a: Asset, kind: FlowKind, amount: Money, on: String) = AssetFlow(ids++, a.id, kind, d(on), amount, null, null)
    private fun valuation(a: Asset, value: Money, on: String) = Valuation(ids++, a.id, d(on), value, null)
    private fun pct(text: String) = BigDecimal(text).setScale(10)

    private val tesouro = asset()
    private val initial = flow(tesouro, FlowKind.INITIAL, brl("5000.00"), "2026-01-10")
    private val march = valuation(tesouro, brl("5120.00"), "2026-03-31")
    private val contribution = flow(tesouro, FlowKind.CONTRIBUTION, brl("1000.00"), "2026-04-15")
    private val partial = flow(tesouro, FlowKind.PARTIAL_DISPOSAL, brl("2000.00"), "2026-05-01")
    private val june = valuation(tesouro, brl("4300.00"), "2026-06-30")
    private val full = flow(tesouro, FlowKind.FULL_DISPOSAL, brl("4350.00"), "2026-07-10")
    private val flows = listOf(initial, contribution, partial, full)
    private val valuations = listOf(march, june)

    private fun at(date: String) = PortfolioCalculator.snapshot(tesouro, flows, valuations, d(date))!!

    @Test
    fun valuation_gainAndReturn() {
        val s = at("2026-03-31")
        assertEquals(brl("5120.00"), s.value)
        assertEquals(brl("120.00"), s.gain) // 5.120 − 5.000
        assertEquals(pct("0.024"), s.simpleReturn) // 120 / 5.000 = 2,40%
        assertFalse(s.carriedForward)
    }

    @Test
    fun contributionAfterValuation_carriesForward_withoutChangingGain() {
        val s = at("2026-04-20")
        assertEquals(brl("6120.00"), s.value) // 5.120 + 1.000
        assertEquals(brl("6000.00"), s.netInvested)
        assertEquals(brl("120.00"), s.gain)
        assertTrue(s.carriedForward)
    }

    @Test
    fun partialDisposal_returnOnTotalContributed() {
        val s = at("2026-05-01")
        assertEquals(brl("4120.00"), s.value) // 6.120 − 2.000
        assertEquals(brl("4000.00"), s.netInvested) // 6.000 − 2.000
        assertEquals(brl("120.00"), s.gain)
        assertEquals(pct("0.02"), s.simpleReturn) // 120 / 6.000
    }

    @Test
    fun laterValuation_andFullDisposal() {
        assertEquals(brl("300.00"), at("2026-06-30").gain) // 4.300 − 4.000
        val closed = at("2026-07-10")
        assertTrue(closed.closed)
        assertEquals(brl("0.00"), closed.value)
        assertEquals(brl("-350.00"), closed.netInvested) // 6.000 − (2.000 + 4.350)
        assertEquals(brl("350.00"), closed.gain)
    }

    @Test
    fun beforeFirstRecord_isNull() {
        assertNull(PortfolioCalculator.snapshot(tesouro, flows, valuations, d("2026-01-09")))
    }

    @Test
    fun sameDayValuation_includesSameDayFlows() {
        val a = asset()
        val f = listOf(flow(a, FlowKind.INITIAL, brl("1000.00"), "2026-01-10"), flow(a, FlowKind.CONTRIBUTION, brl("500.00"), "2026-02-01"))
        val v = listOf(valuation(a, brl("1600.00"), "2026-02-01"))
        val s = PortfolioCalculator.snapshot(a, f, v, d("2026-02-01"))!!
        assertEquals(brl("1600.00"), s.value) // not 1.600 + 500
        assertEquals(brl("100.00"), s.gain)
    }

    @Test
    fun inheritedCar_zeroInitial_hasNoReturn() {
        val car = asset()
        val s = PortfolioCalculator.snapshot(car, listOf(flow(car, FlowKind.INITIAL, brl("0.00"), "2026-01-10")), listOf(valuation(car, brl("40000.00"), "2026-01-10")), d("2026-11-12"))!!
        assertEquals(brl("40000.00"), s.gain)
        assertNull(s.simpleReturn)
    }

    @Test
    fun withdrawalLargerThanValue_clampsAtZero() {
        val a = asset()
        val f = listOf(flow(a, FlowKind.INITIAL, brl("1000.00"), "2026-01-10"), flow(a, FlowKind.WITHDRAWAL, brl("1500.00"), "2026-03-01"))
        val v = listOf(valuation(a, brl("1200.00"), "2026-02-01"))
        assertEquals(brl("0.00"), PortfolioCalculator.snapshot(a, f, v, d("2026-03-01"))!!.value)
    }

    @Test
    fun valuationRows_keepEarlierRowsAsTheyWere() {
        val rows = PortfolioCalculator.valuationRows(tesouro, flows, valuations)
        assertEquals(listOf(brl("120.00"), brl("300.00")), rows.map { it.gain })
        assertEquals(brl("5000.00"), rows[0].netInvested)
        assertEquals(brl("4000.00"), rows[1].netInvested)
    }

    @Test
    fun periodGain_removesNetFlows() {
        // 31/03 → 30/06: (4.300 − 5.120) − (1.000 − 2.000) = 180
        assertEquals(brl("180.00"), PortfolioCalculator.periodGain(tesouro, flows, valuations, d("2026-03-31"), d("2026-06-30")))
        // Asset created inside the period: value at start is 0, flows count: 5.120 − 5.000 = 120
        assertEquals(brl("120.00"), PortfolioCalculator.periodGain(tesouro, flows, valuations, d("2025-12-31"), d("2026-03-31")))
        assertNull(PortfolioCalculator.periodGain(tesouro, flows, valuations, d("2025-01-01"), d("2025-12-31")))
    }

    @Test
    fun usdAsset_gainInBrlIncludesCurrencyEffect() {
        val etf = asset(Currency.USD)
        val f = listOf(flow(etf, FlowKind.INITIAL, usd("1000.00"), "2026-02-02"))
        val v = listOf(valuation(etf, usd("1100.00"), "2026-11-12"))
        val converter = Converter(ExchangeRates(listOf(rate("2026-02-02", "5.0000"), rate("2026-11-12", "5.6143"))))
        val s = PortfolioCalculator.snapshot(etf, f, v, d("2026-11-12"))!!
        assertEquals(usd("100.00"), s.gain)
        val brl = PortfolioCalculator.convertedSnapshot(s, f, d("2026-11-12"), Currency.BRL, converter)
        assertEquals(Total(brl("5000.00")), brl.contributed)
        assertEquals(Total(brl("6175.73")), brl.value) // 1.100 × 5,6143
        assertEquals(Total(brl("1175.73")), brl.gain)
    }

    @Test
    fun totals_openAssetsAndRealizedClosed() {
        val open = asset()
        val data = PortfolioData(
            assets = listOf(tesouro, open),
            flows = mapOf(tesouro.id to flows, open.id to listOf(flow(open, FlowKind.INITIAL, brl("1000.00"), "2026-08-01"))),
            valuations = mapOf(tesouro.id to valuations, open.id to listOf(valuation(open, brl("1100.00"), "2026-10-01"))),
        )
        val t = PortfolioCalculator.totals(data, d("2026-11-12"), Currency.BRL, Converter(ExchangeRates.EMPTY))
        assertEquals(Total(brl("1100.00")), t.value)
        assertEquals(Total(brl("1000.00")), t.invested)
        assertEquals(Total(brl("100.00")), t.gain)
        assertEquals(Total(brl("350.00")), t.realizedClosed)
        assertEquals(d("2026-10-01"), t.lastValuationDate)
        assertEquals(1, t.openCount)
        assertEquals(1, t.closedCount)
    }

    @Test
    fun allocation_sharesSumToOne() {
        val categories = listOf(AssetCategory(1, "A", AssetGroup.INVESTMENT, "x", null, false, 0), AssetCategory(2, "B", AssetGroup.INVESTMENT, "x", null, false, 1), AssetCategory(3, "C", AssetGroup.PERSONAL, "x", null, false, 2))
        val a = asset(category = 1)
        val b = asset(category = 2)
        val c = asset(category = 3)
        val data = PortfolioData(
            assets = listOf(a, b, c),
            flows = mapOf(a.id to listOf(flow(a, FlowKind.INITIAL, brl("6000.00"), "2026-01-10")), b.id to listOf(flow(b, FlowKind.INITIAL, brl("3000.00"), "2026-01-10")), c.id to listOf(flow(c, FlowKind.INITIAL, brl("1000.00"), "2026-01-10"))),
            categories = categories,
        )
        val all = Allocation.of(data, d("2026-11-12"), Currency.BRL, Converter(ExchangeRates.EMPTY))
        assertEquals(listOf(BigDecimal("0.6000"), BigDecimal("0.3000"), BigDecimal("0.1000")), all.map { it.share })
        val investments = Allocation.of(data, d("2026-11-12"), Currency.BRL, Converter(ExchangeRates.EMPTY), AssetGroup.INVESTMENT)
        assertEquals(listOf(1L, 2L), investments.map { it.categoryId })

        // Thirds: 0,3333 × 3 = 0,9999 → the largest slice takes the remainder.
        val thirds = PortfolioData(
            assets = listOf(a, b, c),
            flows = listOf(a, b, c).associate { it.id to listOf(flow(it, FlowKind.INITIAL, brl("100.00"), "2026-01-10")) },
            categories = categories,
        )
        val shares = Allocation.of(thirds, d("2026-11-12"), Currency.BRL, Converter(ExchangeRates.EMPTY)).map { it.share }
        assertEquals(BigDecimal.ONE.setScale(4), shares.reduce(BigDecimal::add))
    }

    @Test
    fun series_startsAtFirstRecord_andEndsToday() {
        val dates = Series.sampleDates(Period.Y1, d("2026-01-10"), d("2026-11-12"))
        assertEquals(d("2026-01-31"), dates.first())
        assertEquals(d("2026-11-12"), dates.last())
        assertEquals(11, dates.size) // Jan..Oct month ends + today
        val month = Series.sampleDates(Period.M1, d("2026-01-10"), d("2026-11-12"))
        assertEquals(d("2026-10-12"), month.first())
        assertEquals(d("2026-11-12"), month.last())
        assertTrue(Series.sampleDates(Period.ALL, d("2026-11-20"), d("2026-11-12")).isEmpty())
    }

    @Test
    fun series_cumulativeGainKeepsClosedAssets() {
        val data = PortfolioData(assets = listOf(tesouro), flows = mapOf(tesouro.id to flows), valuations = mapOf(tesouro.id to valuations))
        val points = Series.portfolio(data, Period.ALL, d("2026-11-12"), Currency.BRL, Converter(ExchangeRates.EMPTY))
        val last = points.last()
        assertEquals(brl("0.00"), last.value.money)
        assertEquals(brl("350.00"), last.gain.money)
        assertEquals(brl("5120.00"), points.first { it.date == d("2026-03-31") }.value.money)
    }
}
