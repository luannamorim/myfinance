package io.github.luannamorim.myfinance.core.format

import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.NumberFormatStyle
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/** True minus sign, used instead of the hyphen for negative amounts. */
const val MINUS = '−'

/** No-break space between a currency symbol and its number. */
const val NBSP = ' '

/**
 * The only place where money turns into text. Formats follow the user's [NumberFormatStyle]
 * (`1.234,56` or `1,234.56`), independent of the UI language. Not thread-safe (DecimalFormat).
 */
class MoneyFormatter(val style: NumberFormatStyle) {

    private val symbols = DecimalFormatSymbols().apply {
        when (style) {
            NumberFormatStyle.COMMA_DECIMAL -> { groupingSeparator = '.'; decimalSeparator = ',' }
            NumberFormatStyle.DOT_DECIMAL -> { groupingSeparator = ','; decimalSeparator = '.' }
        }
        minusSign = MINUS
    }

    private fun decimalFormat(fractionDigits: Int) = DecimalFormat(
        if (fractionDigits == 0) "#,##0" else "#,##0." + "0".repeat(fractionDigits),
        symbols,
    ).apply { roundingMode = RoundingMode.HALF_EVEN }

    /** `R$ 1.234,56`, `−US$ 0,50`; with [signed], positive amounts get a `+`. Zero is never signed. */
    fun format(money: Money, signed: Boolean = false): String {
        val number = formatNumber(Math.absExact(money.minor), money.currency)
        val sign = when {
            money.minor < 0 -> MINUS.toString()
            signed && money.minor > 0 -> "+"
            else -> ""
        }
        return "$sign${money.currency.symbol}$NBSP$number"
    }

    /** The number alone, without symbol: `1.234,56`. */
    fun formatNumber(minor: Long, currency: Currency): String =
        decimalFormat(currency.fractionDigits).format(BigDecimal.valueOf(minor, currency.fractionDigits))

    /** A ratio as a percentage with two decimals: 0.024 → `2,40%`. */
    fun formatPercent(ratio: BigDecimal, signed: Boolean = false): String {
        val pct = ratio.movePointRight(2).setScale(2, RoundingMode.HALF_EVEN)
        val text = decimalFormat(2).format(pct.abs())
        val sign = when {
            pct.signum() < 0 -> MINUS.toString()
            signed && pct.signum() > 0 -> "+"
            else -> ""
        }
        return "$sign$text%"
    }

    /** An exchange rate with four decimals: `5,6143`. */
    fun formatRate(rate: BigDecimal): String = decimalFormat(4).format(rate)

    /** Short axis label: `950`, `8k`, `12,4k`, `1,2M` (sign kept). */
    fun formatShort(minor: Long, currency: Currency): String {
        val major = BigDecimal.valueOf(minor, currency.fractionDigits)
        val abs = major.abs()
        val (value, suffix) = when {
            abs >= BigDecimal(1_000_000) -> major.movePointLeft(6) to "M"
            abs >= BigDecimal(1_000) -> major.movePointLeft(3) to "k"
            else -> major to ""
        }
        val digits = if (suffix.isEmpty()) 0 else 1
        val df = DecimalFormat(if (digits == 0) "#,##0" else "#,##0.#", symbols).apply { roundingMode = RoundingMode.HALF_EVEN }
        return df.format(value.setScale(digits, RoundingMode.HALF_EVEN)) + suffix
    }
}
