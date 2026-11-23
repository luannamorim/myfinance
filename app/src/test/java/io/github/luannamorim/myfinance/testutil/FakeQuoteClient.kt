package io.github.luannamorim.myfinance.testutil

import io.github.luannamorim.myfinance.data.market.QuoteClient
import java.time.LocalDate

/** Yahoo's 404 body for an unknown symbol. */
const val NOT_FOUND_BODY = """{"chart":{"result":null,"error":{"code":"Not Found","description":"No data found, symbol may be delisted"}}}"""

/** A [QuoteClient] that never reaches the network: answers come from [chart] and [search], requests are recorded. */
class FakeQuoteClient(
    var chart: (symbol: String, from: LocalDate) -> String = { _, _ -> NOT_FOUND_BODY },
    var search: (String) -> String = { """{"quotes":[]}""" },
) : QuoteClient {
    val chartRequests: MutableList<Pair<String, LocalDate>> = mutableListOf()
    val searchRequests: MutableList<String> = mutableListOf()

    override suspend fun chart(symbol: String, from: LocalDate, toEpochSecond: Long): String {
        chartRequests += symbol to from
        return chart.invoke(symbol, from)
    }

    override suspend fun search(query: String): String {
        searchRequests += query
        return search.invoke(query)
    }
}

/**
 * A Yahoo chart response for [symbol] with one daily bar per (date, close) at 09:30 New York time, and splits as
 * (date, "numerator:denominator"). Closes are split-adjusted, as Yahoo sends them.
 */
fun chartJson(symbol: String, vararg bars: Pair<String, String>, splits: List<Pair<String, String>> = emptyList()): String {
    fun seconds(date: String) = LocalDate.parse(date).atTime(9, 30).atZone(java.time.ZoneId.of("America/New_York")).toEpochSecond()
    val timestamps = bars.joinToString(",") { seconds(it.first).toString() }
    val closes = bars.joinToString(",") { it.second }
    val events = splits.joinToString(",", """"events":{"splits":{""", "}},") { (date, ratio) ->
        val (n, d) = ratio.split(":")
        val s = seconds(date)
        """"$s":{"date":$s,"numerator":$n,"denominator":$d,"splitRatio":"$ratio"}"""
    }.takeIf { splits.isNotEmpty() }.orEmpty()
    return """{"chart":{"result":[{"meta":{"currency":"USD","symbol":"$symbol","instrumentType":"EQUITY",""" +
        """"exchangeTimezoneName":"America/New_York"},"timestamp":[$timestamps],$events""" +
        """"indicators":{"quote":[{"close":[$closes]}]}}],"error":null}}"""
}
