package io.github.luannamorim.myfinance.domain.fx

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The response wasn't the expected JSON array. */
class RateFormatException(message: String) : Exception(message)

/**
 * Parses the Banco Central SGS response: `[{"data":"12/11/2026","valor":"5.6143"}, …]`.
 * Malformed entries and rates outside 0.1..100 are dropped; anything but a JSON array is an error.
 */
object PtaxParser {
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val MIN = BigDecimal("0.1")
    private val MAX = BigDecimal("100")

    fun parse(json: String): List<Pair<LocalDate, BigDecimal>> {
        val root = try {
            Json.parseToJsonElement(json)
        } catch (e: Exception) {
            throw RateFormatException("Not JSON")
        }
        if (root !is JsonArray) throw RateFormatException("Not a JSON array")
        return root.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val dateText = (obj["data"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
            val valueText = (obj["valor"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(dateText, dateFormat) }.getOrNull() ?: return@mapNotNull null
            val rate = valueText.toBigDecimalOrNull() ?: return@mapNotNull null
            if (rate < MIN || rate > MAX) null else date to rate
        }
    }
}
