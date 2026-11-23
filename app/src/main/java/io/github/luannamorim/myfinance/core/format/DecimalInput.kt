package io.github.luannamorim.myfinance.core.format

import io.github.luannamorim.myfinance.domain.model.NumberFormatStyle
import java.math.BigDecimal

/**
 * A typed decimal in the app's number format: COMMA_DECIMAL "1.234,56" (dots only as 3-digit groups), DOT_DECIMAL
 * "1,234.56". So "1.000" is one thousand in the `1.234,56` format, never 1. Empty is null.
 */
object DecimalInput {
    /** Integer digits accepted: up to 999 999 999 999, so quantity × price stays far from overflowing [Long] minor units. */
    private const val MAX_INTEGER_DIGITS = 12

    /** Integer part (plain or in 3-digit groups) and an optional decimal part of any length, checked against maxDecimals. */
    private val COMMA_PATTERN = Regex("""^(\d{1,$MAX_INTEGER_DIGITS}|\d{1,3}(\.\d{3}){1,3})(,(\d+))?$""")
    private val DOT_PATTERN = Regex("""^(\d{1,$MAX_INTEGER_DIGITS}|\d{1,3}(,\d{3}){1,3})(\.(\d+))?$""")

    fun parse(text: String, style: NumberFormatStyle, maxDecimals: Int = 8): Result<BigDecimal?> {
        val t = text.trim()
        if (t.isEmpty()) return Result.success(null)
        val (group, decimal, pattern) = when (style) {
            NumberFormatStyle.COMMA_DECIMAL -> Triple('.', ',', COMMA_PATTERN)
            NumberFormatStyle.DOT_DECIMAL -> Triple(',', '.', DOT_PATTERN)
        }
        val match = pattern.matchEntire(t)
        if (match == null || match.groupValues[4].length > maxDecimals) return Result.failure(IllegalArgumentException("Invalid number"))
        return Result.success(BigDecimal(t.replace(group.toString(), "").replace(decimal, '.')))
    }

    /** "1.234,56" or "1,234.56": an example number for error messages. */
    fun example(style: NumberFormatStyle): String = when (style) {
        NumberFormatStyle.COMMA_DECIMAL -> "1.234,56"
        NumberFormatStyle.DOT_DECIMAL -> "1,234.56"
    }
}
