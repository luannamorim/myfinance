package io.github.luannamorim.myfinance.domain.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A Yahoo response wasn't the expected JSON (or was for another currency). */
class QuoteFormatException(message: String) : Exception(message)

/** One symbol's chart: raw (not split-adjusted) closes and its splits. */
data class ChartResult(
    val symbol: String,
    val instrumentType: String?,
    val longName: String?,
    val firstTradeDate: LocalDate?,
    val prices: List<PricePoint>,
    val splits: List<Split>,
)

sealed interface ChartResponse {
    data class Found(val result: ChartResult) : ChartResponse
    data object NotFound : ChartResponse
}

/**
 * Parses the Yahoo v8 chart response. Closes are read from their JSON literal (never a Double) and,
 * because Yahoo returns split-adjusted closes, multiplied back by every split after the bar's date,
 * then rounded to 7 significant digits.
 *
 * Bounds: bars and splits dated after `maxDate` (e.g. a timestamp in milliseconds instead of seconds), closes outside
 * [[MIN_CLOSE], [MAX_CLOSE]] (or with more than [MAX_PRICE_SCALE] decimals once rounded), split terms above
 * [MAX_SPLIT_TERM] or with more than [MAX_TERM_SCALE] decimals, and split ratios outside [1/1000, 1000] are dropped.
 * Literals are range-checked before any arithmetic, so an absurd exponent (`1e-999999999`) never reaches it.
 */
object YahooChartParser {
    private val DEFAULT_ZONE = ZoneId.of("America/New_York")
    private val SEVEN_DIGITS = MathContext(7, RoundingMode.HALF_EVEN)
    private val MAX_CLOSE = BigDecimal(10_000_000)
    private val MIN_CLOSE = BigDecimal("1e-12")
    private const val MAX_PRICE_SCALE = 20
    private val MAX_SPLIT_RATIO = BigDecimal(1000)
    private val MAX_SPLIT_TERM = BigDecimal(1_000_000)
    private const val MAX_TERM_SCALE = 10

    /** [maxDate]: the last plausible bar or split date (the caller passes "tomorrow"); null keeps every date. */
    fun parse(json: String, maxDate: LocalDate? = null): ChartResponse {
        val root = try {
            Json.parseToJsonElement(json)
        } catch (e: Exception) {
            throw QuoteFormatException("Not JSON")
        }
        val chart = (root as? JsonObject)?.get("chart") as? JsonObject ?: throw QuoteFormatException("No chart")
        val error = chart["error"] as? JsonObject
        if (error != null && text(error["code"]) == "Not Found") return ChartResponse.NotFound
        val result = ((chart["result"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?: throw QuoteFormatException("No chart result")
        val meta = result["meta"] as? JsonObject ?: throw QuoteFormatException("No meta")
        if (text(meta["currency"]) != "USD") throw QuoteFormatException("Not a USD quote")
        val symbol = text(meta["symbol"]) ?: throw QuoteFormatException("No symbol")
        val zone = text(meta["exchangeTimezoneName"])?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: DEFAULT_ZONE

        val splits = splitsOf(result, zone).filter { maxDate == null || it.date <= maxDate }
        val prices = pricesOf(result, zone, splits).filter { maxDate == null || it.date <= maxDate }
        val firstTrade = (meta["firstTradeDate"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?.let { dateOf(it, zone) }
        return ChartResponse.Found(
            ChartResult(symbol, text(meta["instrumentType"]), text(meta["longName"]), firstTrade, prices, splits),
        )
    }

    private fun text(e: JsonElement?): String? = (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    private fun dateOf(epochSeconds: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()

    private fun splitsOf(result: JsonObject, zone: ZoneId): List<Split> {
        val map = ((result["events"] as? JsonObject)?.get("splits") as? JsonObject) ?: return emptyList()
        return map.values.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val seconds = text(obj["date"])?.toLongOrNull() ?: return@mapNotNull null
            val numerator = text(obj["numerator"])?.toBigDecimalOrNull() ?: return@mapNotNull null
            val denominator = text(obj["denominator"])?.toBigDecimalOrNull() ?: return@mapNotNull null
            if (!plausibleTerm(numerator) || !plausibleTerm(denominator)) return@mapNotNull null
            if (numerator > denominator.multiply(MAX_SPLIT_RATIO) || denominator > numerator.multiply(MAX_SPLIT_RATIO)) {
                return@mapNotNull null
            }
            Split(dateOf(seconds, zone), numerator, denominator)
        }.sortedBy { it.date }
    }

    /** A split numerator or denominator: positive, at most [MAX_SPLIT_TERM], at most [MAX_TERM_SCALE] decimals. */
    private fun plausibleTerm(term: BigDecimal): Boolean =
        term.signum() > 0 && term <= MAX_SPLIT_TERM && term.stripTrailingZeros().scale() <= MAX_TERM_SCALE

    private fun pricesOf(result: JsonObject, zone: ZoneId, splits: List<Split>): List<PricePoint> {
        val timestamps = result["timestamp"] as? JsonArray ?: return emptyList()
        val closes = (((result["indicators"] as? JsonObject)?.get("quote") as? JsonArray)
            ?.firstOrNull() as? JsonObject)?.get("close") as? JsonArray ?: return emptyList()
        return timestamps.mapIndexedNotNull { i, t ->
            val seconds = text(t)?.toLongOrNull() ?: return@mapIndexedNotNull null
            val close = text(closes.getOrNull(i))?.toBigDecimalOrNull() ?: return@mapIndexedNotNull null
            if (close < MIN_CLOSE || close > MAX_CLOSE.multiply(MAX_SPLIT_RATIO)) return@mapIndexedNotNull null
            val date = dateOf(seconds, zone)
            val later = splits.filter { it.date > date }
            val raw = if (later.isEmpty()) {
                close.round(SEVEN_DIGITS)
            } else {
                var numerator = close
                var denominator = BigDecimal.ONE
                later.forEach {
                    numerator = numerator.multiply(it.numerator)
                    denominator = denominator.multiply(it.denominator)
                }
                numerator.divide(denominator, 10, RoundingMode.HALF_EVEN).round(SEVEN_DIGITS)
            }
            if (raw < MIN_CLOSE || raw > MAX_CLOSE || raw.scale() > MAX_PRICE_SCALE) return@mapIndexedNotNull null
            PricePoint(date, raw)
        }
    }
}
