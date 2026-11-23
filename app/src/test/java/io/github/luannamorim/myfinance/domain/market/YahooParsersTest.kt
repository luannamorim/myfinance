package io.github.luannamorim.myfinance.domain.market

import io.github.luannamorim.myfinance.testutil.d
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class YahooParsersTest {

    private fun resource(name: String) = javaClass.getResource("/yahoo/$name")!!.readText()

    private fun found(json: String, maxDate: java.time.LocalDate? = null): ChartResult =
        (YahooChartParser.parse(json, maxDate) as ChartResponse.Found).result

    private fun assertPrice(expected: String, actual: BigDecimal) =
        assertEquals("expected $expected got $actual", 0, BigDecimal(expected).compareTo(actual))

    private fun chartJson(tz: String, timestamps: String, closes: String, currency: String = "USD", splits: String = "") =
        """{"chart":{"result":[{"meta":{"currency":"$currency","symbol":"X","exchangeTimezoneName":"$tz"},
            "timestamp":[$timestamps],${if (splits.isEmpty()) "" else """"events":{"splits":{$splits}},"""}
            "indicators":{"quote":[{"close":[$closes]}]}}],"error":null}}"""

    @Test
    fun chart_rawPricesUndoLaterSplit() {
        val r = found(resource("nvda_three_bars.json"))
        assertEquals("NVDA", r.symbol)
        assertEquals("EQUITY", r.instrumentType)
        assertEquals("NVIDIA Corporation", r.longName)
        assertEquals(listOf(d("2024-06-06"), d("2024-06-07"), d("2024-06-10")), r.prices.map { it.date })
        assertPrice("1209.980", r.prices[0].price)
        assertPrice("1208.880", r.prices[1].price)
        assertPrice("121.7900", r.prices[2].price)
        val split = r.splits.single()
        assertEquals(d("2024-06-10"), split.date)
        assertEquals(0, BigDecimal(10).compareTo(split.numerator))
        assertEquals(0, BigDecimal(1).compareTo(split.denominator))
        assertEquals(d("1999-01-22"), r.firstTradeDate)
    }

    @Test
    fun chart_nvdaFixture_hasFourBars() {
        val r = found(resource("nvda_split.json"))
        assertEquals(4, r.prices.size)
        assertPrice("120.9100", r.prices[3].price)
    }

    @Test
    fun chart_reverseSplit() {
        val r = found(resource("ge_reverse.json"))
        assertEquals(d("2021-08-02"), r.splits.single().date)
        val mc = MathContext(7, RoundingMode.HALF_EVEN)
        val before = BigDecimal("66.23915100097656").divide(BigDecimal(8), 10, RoundingMode.HALF_EVEN).round(mc)
        assertEquals(d("2021-07-29"), r.prices[0].date)
        assertPrice(before.toPlainString(), r.prices[0].price)
        assertPrice(BigDecimal("62.67549514770508").round(mc).toPlainString(), r.prices[2].price)
        assertEquals(d("2021-08-02"), r.prices[2].date)
    }

    @Test
    fun chart_tinyPrice_keepsPrecision() {
        val r = found(chartJson("UTC", "1795046400,1795132800", "3.999999989900971e-06,3.789999936998356e-06"))
        assertPrice("0.000004", r.prices[0].price)
        assertPrice("0.00000379", r.prices[1].price)
    }

    @Test
    fun chart_shibFixture_acceptsUpperCaseExponent() {
        val r = found(resource("shib.json"))
        assertPrice("0.000004", r.prices[0].price)
        assertPrice("0.00000376", r.prices[4].price)
    }

    @Test
    fun chart_crypto() {
        val r = found(chartJson("UTC", "1795046400,1795132800", "63336.83984375,1755.1898193359375"))
        assertPrice("63336.84", r.prices[0].price)
        assertPrice("1755.190", r.prices[1].price)
        assertEquals(5, found(resource("btc.json")).prices.size)
    }

    @Test
    fun cryptoBarDate_usesUtc() {
        val r = found(chartJson("UTC", "1795392000", "100"))
        assertEquals(d("2026-11-23"), r.prices.single().date)
    }

    @Test
    fun barDate_usesNewYorkDate() {
        val r = found(chartJson("America/New_York", "1795224600", "100"))
        assertEquals(d("2026-11-20"), r.prices.single().date)
    }

    @Test
    fun barDate_missingTimezoneFallsBackToNewYork() {
        val json = """{"chart":{"result":[{"meta":{"currency":"USD","symbol":"X"},"timestamp":[1795224600],
            "indicators":{"quote":[{"close":[100]}]}}],"error":null}}"""
        assertEquals(d("2026-11-20"), found(json).prices.single().date)
    }

    @Test
    fun chart_nullCloseSkipped() {
        val r = found(chartJson("America/New_York", "1717680600,1717767000,1718026200", "10,null,0"))
        assertEquals(listOf(d("2024-06-06")), r.prices.map { it.date })
    }

    @Test
    fun chart_barAfterMaxDate_dropped() {
        // 1795392000 is 2026-11-23 00:00 UTC; the second timestamp is the same instant in milliseconds.
        val r = found(chartJson("UTC", "1795392000,1795392000000", "100,101"), maxDate = d("2026-11-24"))
        assertEquals(listOf(d("2026-11-23")), r.prices.map { it.date })
    }

    @Test
    fun chart_splitAfterMaxDate_droppedAndNotApplied() {
        val r = found(
            chartJson("UTC", "1795392000", "100", splits = """"1795392000000":{"date":1795392000000,"numerator":10,"denominator":1}"""),
            maxDate = d("2026-11-24"),
        )
        assertTrue(r.splits.isEmpty())
        assertPrice("100", r.prices.single().price)
    }

    @Test
    fun chart_absurdCloseDropped() {
        val r = found(chartJson("UTC", "1795046400,1795132800,1795219200", "10000000,10000100,5e9"))
        assertEquals(listOf(d("2026-11-19")), r.prices.map { it.date })
    }

    @Test
    fun chart_splitRatioOutOfRange_dropped() {
        val splits = """"1795132800":{"date":1795132800,"numerator":1001,"denominator":1},""" +
            """"1795219200":{"date":1795219200,"numerator":1000,"denominator":1}"""
        val r = found(chartJson("UTC", "1795046400", "100", splits = splits))
        assertEquals(listOf(d("2026-11-21")), r.splits.map { it.date })
    }

    @Test
    fun chart_noLaterSplit_keepsExactCloseBeforeRounding() {
        val r = found(chartJson("UTC", "1795046400", "1.234567890123e-08"))
        assertPrice("0.00000001234568", r.prices.single().price)
    }

    @Test
    fun chart_weekend_isEmpty() {
        val r = found(resource("weekend.json"))
        assertTrue(r.prices.isEmpty())
        assertTrue(r.splits.isEmpty())
    }

    @Test
    fun chart_notFound() {
        assertEquals(ChartResponse.NotFound, YahooChartParser.parse(resource("not_found.json")))
    }

    @Test
    fun chart_eur_rejected() {
        try {
            YahooChartParser.parse(chartJson("Europe/Berlin", "1717680600", "10", currency = "EUR"))
            fail()
        } catch (_: QuoteFormatException) {
        }
    }

    @Test
    fun chart_html_rejected() {
        try {
            YahooChartParser.parse("<html>Too Many Requests</html>")
            fail()
        } catch (_: QuoteFormatException) {
        }
    }

    @Test
    fun chart_missingResult_rejected() {
        try {
            YahooChartParser.parse("""{"chart":{"result":[],"error":null}}""")
            fail()
        } catch (_: QuoteFormatException) {
        }
    }

    @Test
    fun search_keepsUsStocksAndEtfs() {
        val m = YahooSearchParser.parse(resource("search_nvd.json"))
        assertEquals(TickerType.STOCK, m.single { it.symbol == "NVDA" }.type)
        assertEquals("NVIDIA Corporation", m.single { it.symbol == "NVDA" }.name)
        assertEquals("NASDAQ", m.single { it.symbol == "NVDA" }.exchange)
        assertEquals(TickerType.ETF, m.single { it.symbol == "NVDL" }.type)
        assertEquals("T-Rex 2X Long NVIDIA Daily Targ", m.single { it.symbol == "NVDX" }.name)
        assertFalse(m.any { it.symbol == "NVD.DE" })
        assertFalse(m.any { '=' in it.symbol })
        assertTrue(m.size <= 8)
    }

    @Test
    fun search_keepsUsdCrypto() {
        val btc = YahooSearchParser.parse(resource("search_bitcoin.json"))
        assertEquals(TickerType.CRYPTO, btc.single { it.symbol == "BTC-USD" }.type)
        assertEquals(TickerType.ETF, btc.single { it.symbol == "IBIT" }.type)
        assertNull(btc.firstOrNull { it.symbol == "BTC=F" })
        val sol = YahooSearchParser.parse(resource("search_solana.json"))
        assertTrue(sol.any { it.symbol == "SOL-USD" })
        assertFalse(sol.any { it.symbol == "SOL-CAD" || it.symbol == "SOL-GBP" })
    }

    @Test
    fun search_capsAtEight_andRejectsNonJson() {
        val quotes = (1..12).joinToString(",") { """{"symbol":"AB$it","quoteType":"EQUITY","exchange":"NMS"}""" }
        val m = YahooSearchParser.parse("""{"quotes":[$quotes]}""")
        assertEquals(8, m.size)
        assertEquals("AB1", m.first().symbol)
        assertEquals("NMS", m.first().exchange)
        assertEquals("AB1", m.first().name)
        try {
            YahooSearchParser.parse("nope")
            fail()
        } catch (_: QuoteFormatException) {
        }
    }
}
