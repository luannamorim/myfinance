package io.github.luannamorim.myfinance.core.format

import io.github.luannamorim.myfinance.domain.model.NumberFormatStyle
import java.math.BigDecimal

/**
 * A typed decimal in the app's number format: COMMA_DECIMAL "1.234,56" (dots only as 3-digit groups), DOT_DECIMAL
 * "1,234.56". So "1.000" is one thousand in the `1.234,56` format, never 1. Empty is null.
 */
object DecimalInput {
    private const val MAX_INTEGER_DIGITS = 15

    fun parse(text: String, style: NumberFormatStyle, maxDecimals: Int = 8): Result<BigDecimal?> {
        val t = text.trim()
        if (t.isEmpty()) return Result.success(null)
        val (group, decimal) = when (style) {
            NumberFormatStyle.COMMA_DECIMAL -> '.' to ','
            NumberFormatStyle.DOT_DECIMAL -> ',' to '.'
        }
        val g = Regex.escape(group.toString())
        val dsep = Regex.escape(decimal.toString())
        val pattern = Regex("""^(\d{1,$MAX_INTEGER_DIGITS}|\d{1,3}($g\d{3}){1,4})($dsep\d{1,$maxDecimals})?$""")
        if (!pattern.matches(t)) return Result.failure(IllegalArgumentException("Invalid number"))
        return Result.success(BigDecimal(t.replace(group.toString(), "").replace(decimal, '.')))
    }

    /** "1.234,56" or "1,234.56": an example number for error messages. */
    fun example(style: NumberFormatStyle): String = when (style) {
        NumberFormatStyle.COMMA_DECIMAL -> "1.234,56"
        NumberFormatStyle.DOT_DECIMAL -> "1,234.56"
    }
}
