package io.github.luannamorim.myfinance.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * An amount of money in integer minor units (cents) of [currency]. Never use floating point for money:
 * conversions and percentages go through [BigDecimal] and are rounded once with [RoundingMode.HALF_EVEN].
 */
data class Money(val minor: Long, val currency: Currency) {

    operator fun plus(other: Money): Money {
        requireSame(other)
        return Money(Math.addExact(minor, other.minor), currency)
    }

    operator fun minus(other: Money): Money {
        requireSame(other)
        return Money(Math.subtractExact(minor, other.minor), currency)
    }

    operator fun unaryMinus(): Money = Money(Math.negateExact(minor), currency)

    operator fun compareTo(other: Money): Int {
        requireSame(other)
        return minor.compareTo(other.minor)
    }

    val isNegative: Boolean get() = minor < 0
    val isZero: Boolean get() = minor == 0L

    /** Major units as an exact decimal (e.g. 1234.56). */
    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(minor, currency.fractionDigits)

    private fun requireSame(other: Money) =
        require(currency == other.currency) { "Currency mismatch: ${currency.code} vs ${other.currency.code}" }

    companion object {
        fun zero(currency: Currency) = Money(0, currency)

        /** Rounds [major] (e.g. 10.005) to minor units with HALF_EVEN. */
        fun of(major: BigDecimal, currency: Currency): Money =
            Money(major.setScale(currency.fractionDigits, RoundingMode.HALF_EVEN).unscaledValue().longValueExact(), currency)
    }
}

/** Sum of amounts in the same [currency]; an empty list is zero. */
fun Iterable<Money>.sum(currency: Currency): Money = fold(Money.zero(currency)) { acc, m -> acc + m }
