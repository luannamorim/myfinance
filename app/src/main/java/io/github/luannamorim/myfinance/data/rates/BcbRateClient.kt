package io.github.luannamorim.myfinance.data.rates

import io.github.luannamorim.myfinance.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

/** Fetches USD→BRL PTAX rates for a date range; returns the JSON array (empty when there are none). */
fun interface RateClient {
    suspend fun fetch(from: LocalDate, to: LocalDate): String
}

/**
 * Banco Central do Brasil SGS series 1 (Dólar americano, venda, PTAX). HTTPS only, no identifiers: the request
 * carries only the date range (docs/SECURITY.md). Ranges must be at most 10 years.
 */
@Singleton
class BcbRateClient @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) : RateClient {

    override suspend fun fetch(from: LocalDate, to: LocalDate): String = withContext(io) {
        val connection = URL(url(from, to)).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "MyFinance")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { readLimited(it) }.orEmpty()
            interpret(code, body)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE = "https://api.bcb.gov.br/dados/serie/bcdata.sgs.1/dados"
        private const val TIMEOUT_MS = 10_000
        private const val MAX_BYTES = 1 shl 20
        private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        fun url(from: LocalDate, to: LocalDate): String =
            "$BASE?formato=json&dataInicial=${dateFormat.format(from)}&dataFinal=${dateFormat.format(to)}"

        /**
         * A range without business-day rates (weekend, holiday, today before ~13:00) answers HTTP 404 with
         * `"Value(s) not found"`: that is an empty result, not an error.
         */
        internal fun interpret(code: Int, body: String): String = when {
            code in 200..299 -> body
            code == 404 && body.contains("Value(s) not found") -> "[]"
            else -> throw IOException("HTTP $code")
        }

        private fun readLimited(input: InputStream): String {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_BYTES) throw IOException("Response too large")
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }
}
