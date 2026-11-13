package io.github.luannamorim.myfinance.domain.fx

import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.ExchangeRate
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.RateSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.TreeMap

/**
 * USD→BRL rates by date. The rate on a date is the latest one on or before it (weekends and holidays use the
 * last business day); on the same date a MANUAL rate wins over a BCB one. No rate is ever invented.
 */
class ExchangeRates(rates: List<ExchangeRate>) {

    private val byDate = TreeMap<LocalDate, ExchangeRate>().apply {
        rates.sortedBy { if (it.source == RateSource.MANUAL) 1 else 0 }.forEach { put(it.date, it) }
    }

    fun rateOn(date: LocalDate): ExchangeRate? = byDate.floorEntry(date)?.value

    fun latest(): ExchangeRate? = byDate.lastEntry()?.value

    fun first(): ExchangeRate? = byDate.firstEntry()?.value

    val isEmpty: Boolean get() = byDate.isEmpty()

    companion object {
        val EMPTY = ExchangeRates(emptyList())
    }
}

/** A converted total. When some item had no usable rate, [money] holds the sum of the others and [incomplete] is true. */
data class Total(val money: Money, val incomplete: Boolean = false) {
    operator fun plus(other: Total) = Total(money + other.money, incomplete || other.incomplete)
    operator fun minus(other: Total) = Total(money - other.money, incomplete || other.incomplete)

    companion object {
        fun zero(currency: Currency) = Total(Money.zero(currency))
    }
}

/**
 * Converts money with [rates]. Intermediate values use scale 10 and HALF_EVEN; sums are rounded to minor
 * units once, at the end. A null date means "the latest rate" (current values and forecasts).
 */
class Converter(val rates: ExchangeRates) {

    /** [money] in [to], major units at scale 10; null when there is no rate for that date. */
    fun exact(money: Money, to: Currency, on: LocalDate?): BigDecimal? {
        val amount = money.toBigDecimal()
        if (money.currency == to || money.isZero) return amount.setScale(SCALE, RoundingMode.HALF_EVEN)
        val rate = (if (on == null) rates.latest() else rates.rateOn(on))?.rate ?: return null
        return when {
            money.currency == Currency.USD && to == Currency.BRL -> amount.multiply(rate).setScale(SCALE, RoundingMode.HALF_EVEN)
            money.currency == Currency.BRL && to == Currency.USD -> amount.divide(rate, SCALE, RoundingMode.HALF_EVEN)
            else -> null
        }
    }

    fun convert(money: Money, to: Currency, on: LocalDate?): Money? = exact(money, to, on)?.let { Money.of(it, to) }

    /** Sum of items, each converted at its own date (null = latest rate), rounded once. */
    fun sum(items: Iterable<Pair<Money, LocalDate?>>, to: Currency): Total {
        var total = BigDecimal.ZERO
        var incomplete = false
        items.forEach { (money, on) ->
            val converted = exact(money, to, on)
            if (converted == null) incomplete = true else total += converted
        }
        return Total(Money.of(total, to), incomplete)
    }

    companion object {
        const val SCALE = 10
    }
}
