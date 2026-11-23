package io.github.luannamorim.myfinance.data.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class YahooQuoteClientTest {
    @Test
    fun chartUrl_buildsPeriodsAndSplitEvents() {
        assertEquals(
            "https://query1.finance.yahoo.com/v8/finance/chart/VOO?period1=1769385600&period2=1794528000&interval=1d&events=split",
            YahooQuoteClient.chartUrl("VOO", LocalDate.parse("2026-01-26"), 1794528000),
        )
    }

    @Test
    fun chartUrl_rejectsUnsafeSymbol() {
        assertThrows(IllegalArgumentException::class.java) {
            YahooQuoteClient.chartUrl("../x", LocalDate.parse("2026-01-26"), 1794528000)
        }
    }

    @Test
    fun searchUrl_encodesQuery() {
        val url = YahooQuoteClient.searchUrl("vanguard s&p")
        assertTrue(url.startsWith("https://query2.finance.yahoo.com/v1/finance/search"))
        assertTrue(url.endsWith("?q=vanguard+s%26p&quotesCount=10&newsCount=0&listsCount=0"))
    }

    @Test
    fun interpret_passesSuccessThrough() {
        assertEquals("{}", YahooQuoteClient.interpret(200, "{}"))
    }

    @Test
    fun interpret_notFoundBodyIsReturned() {
        val body = """{"chart":{"error":{"code":"Not Found"}}}"""
        assertEquals(body, YahooQuoteClient.interpret(404, body))
    }

    @Test
    fun interpret_otherStatusThrows() {
        assertThrows(IOException::class.java) { YahooQuoteClient.interpret(429, "Too Many Requests") }
    }

    @Test
    fun interpret_404WithoutNotFoundThrows() {
        assertThrows(IOException::class.java) { YahooQuoteClient.interpret(404, "gone") }
    }
}
