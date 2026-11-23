package io.github.luannamorim.myfinance.data.market

import io.github.luannamorim.myfinance.di.IoDispatcher
import io.github.luannamorim.myfinance.domain.market.Ticker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

/** Fetches raw Yahoo Finance JSON: daily charts with splits, and ticker search. */
interface QuoteClient {
    /** Chart JSON for [symbol] from [from] (00:00 UTC) to [toEpochSecond]; a 404 "Not Found" body is returned, not thrown. */
    suspend fun chart(symbol: String, from: LocalDate, toEpochSecond: Long): String

    suspend fun search(query: String): String
}

/**
 * Yahoo Finance over HTTPS only. The requests carry only the ticker or the search text; nothing is logged.
 */
@Singleton
class YahooQuoteClient @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) : QuoteClient {

    override suspend fun chart(symbol: String, from: LocalDate, toEpochSecond: Long): String =
        get(chartUrl(symbol, from, toEpochSecond), CHART_MAX_BYTES)

    override suspend fun search(query: String): String = get(searchUrl(query), SEARCH_MAX_BYTES)

    private suspend fun get(url: String, maxBytes: Int): String = withContext(io) {
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "MyFinance")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { readLimited(it, maxBytes) }.orEmpty()
            interpret(code, body)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val CHART_BASE = "https://query1.finance.yahoo.com/v8/finance/chart/"
        private const val SEARCH_BASE = "https://query2.finance.yahoo.com/v1/finance/search"
        private const val TIMEOUT_MS = 10_000
        private const val CHART_MAX_BYTES = 4 shl 20
        private const val SEARCH_MAX_BYTES = 256 shl 10

        fun chartUrl(symbol: String, from: LocalDate, toEpochSecond: Long): String {
            require(Ticker.normalize(symbol) == symbol) { "Invalid symbol" }
            val period1 = from.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
            return "$CHART_BASE$symbol?period1=$period1&period2=$toEpochSecond&interval=1d&events=split"
        }

        fun searchUrl(query: String): String =
            "$SEARCH_BASE?q=${URLEncoder.encode(query, "UTF-8")}&quotesCount=10&newsCount=0&listsCount=0"

        /** An unknown symbol answers HTTP 404 with a `"Not Found"` body: the parser turns it into "no data". */
        internal fun interpret(code: Int, body: String): String = when {
            code in 200..299 -> body
            code == 404 && body.contains("Not Found") -> body
            else -> throw IOException("HTTP $code")
        }

        private fun readLimited(input: InputStream, maxBytes: Int): String {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > maxBytes) throw IOException("Response too large")
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }
}
