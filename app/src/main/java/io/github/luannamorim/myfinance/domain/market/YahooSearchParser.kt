package io.github.luannamorim.myfinance.domain.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class TickerType { STOCK, ETF, CRYPTO }

data class TickerMatch(val symbol: String, val name: String, val type: TickerType, val exchange: String)

/** Parses the Yahoo v1 search response, keeping only symbols the app can track (US stocks, ETFs, USD crypto). */
object YahooSearchParser {
    private const val MAX_RESULTS = 8

    fun parse(json: String): List<TickerMatch> {
        val root = try {
            Json.parseToJsonElement(json)
        } catch (e: Exception) {
            throw QuoteFormatException("Not JSON")
        }
        val quotes = (root as? JsonObject)?.get("quotes") as? JsonArray ?: throw QuoteFormatException("No quotes")
        return quotes.mapNotNull { match(it) }.take(MAX_RESULTS)
    }

    private fun text(e: JsonElement?): String? = (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    private fun match(element: JsonElement): TickerMatch? {
        val obj = element as? JsonObject ?: return null
        val symbol = text(obj["symbol"]) ?: return null
        if (Ticker.normalize(symbol) != symbol) return null
        val type = when (text(obj["quoteType"])) {
            "EQUITY" -> TickerType.STOCK
            "ETF" -> TickerType.ETF
            "CRYPTOCURRENCY" -> if (Ticker.isCrypto(symbol)) TickerType.CRYPTO else return null
            else -> return null
        }
        val name = text(obj["longname"]) ?: text(obj["shortname"]) ?: symbol
        val exchange = text(obj["exchDisp"]) ?: text(obj["exchange"]) ?: ""
        return TickerMatch(symbol, name, type, exchange)
    }
}
