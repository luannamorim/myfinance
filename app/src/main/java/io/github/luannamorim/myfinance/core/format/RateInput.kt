package io.github.luannamorim.myfinance.core.format

import java.math.BigDecimal

/** Parses a typed exchange rate: "5,6143" or "5.6143", up to 6 decimals, between 0.1 and 100. */
object RateInput {
    private val pattern = Regex("""^\d{1,3}([.,]\d{1,6})?$""")

    fun parse(text: String): BigDecimal? {
        val t = text.trim()
        if (!pattern.matches(t)) return null
        val value = BigDecimal(t.replace(',', '.'))
        return value.takeIf { it >= BigDecimal("0.1") && it <= BigDecimal("100") }
    }
}
