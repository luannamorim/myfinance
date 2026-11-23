package io.github.luannamorim.myfinance.domain.market

import io.github.luannamorim.myfinance.testutil.d
import java.math.BigDecimal
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class MarketDataTest {

    @Test fun normalize_trimsAndUppercases() {
        assertEquals("BRK-B", Ticker.normalize(" brk-b "))
        assertEquals("AAPL", Ticker.normalize("aapl"))
    }

    @Test fun normalize_cryptoKept() {
        assertEquals("BTC-USD", Ticker.normalize("btc-usd"))
        assertEquals("USDE29470-USD", Ticker.normalize("USDE29470-USD"))
    }

    @Test fun normalize_rejectsOutOfScope() {
        listOf("NVD.DE", "SAAPL=F", "SOL-CAD", "", "TOOLONGX").forEach { assertNull(it, Ticker.normalize(it)) }
    }

    @Test fun cryptoDetection() {
        assertTrue(Ticker.isCrypto("BTC-USD"))
        assertFalse(Ticker.isCrypto("BRK-B"))
        assertEquals("BTC", Ticker.unitCode("BTC-USD"))
        assertNull(Ticker.unitCode("NVDA"))
    }

    private fun history() = SymbolHistory(
        prices = listOf(PricePoint(d("2026-11-20"), BigDecimal("342.56")), PricePoint(d("2026-11-23"), BigDecimal("348.63"))),
        splits = emptyList(),
    )

    @Test fun priceOn_usesLatestOnOrBefore() {
        val data = MarketData(mapOf("AAPL" to history()))
        assertEquals(PricePoint(d("2026-11-20"), BigDecimal("342.56")), data.priceOn("AAPL", d("2026-11-22")))
        assertEquals(PricePoint(d("2026-11-23"), BigDecimal("348.63")), data.priceOn("AAPL", d("2026-11-23")))
        assertNull(data.priceOn("AAPL", d("2026-11-19")))
    }

    @Test fun empty_hasNoPrices() {
        assertNull(MarketData.EMPTY.priceOn("AAPL", d("2026-11-23")))
        assertTrue(MarketData.EMPTY.splitsOf("AAPL").isEmpty())
    }

    @Test fun splitsOf_sortedByDate() {
        val late = Split(d("2024-06-10"), BigDecimal(10), BigDecimal.ONE)
        val early = Split(d("2020-08-31"), BigDecimal(4), BigDecimal.ONE)
        val data = MarketData(mapOf("X" to SymbolHistory(emptyList(), listOf(late, early))))
        assertEquals(listOf(early, late), data.splitsOf("X"))
    }
}
