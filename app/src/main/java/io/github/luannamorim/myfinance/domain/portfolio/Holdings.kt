package io.github.luannamorim.myfinance.domain.portfolio

import io.github.luannamorim.myfinance.domain.market.Split
import io.github.luannamorim.myfinance.domain.model.AssetFlow
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Share counts of tracked assets over time. Flow quantities are as of the flow's date; splits rescale what is held. */
object Holdings {
    private const val SCALE = 10

    /** Shares held at the end of [at], or null when a flow up to then has no quantity. */
    fun quantityAt(flows: List<AssetFlow>, splits: List<Split>, at: LocalDate): BigDecimal? {
        if (flows.any { !it.date.isAfter(at) && it.quantityDelta == null }) return null
        var quantity = BigDecimal.ZERO
        walk(flows, splits, at) { _, q -> quantity = q }
        return quantity
    }

    /** The first date on which the shares held drop below zero after that day's flows, or null. */
    fun firstShortfall(flows: List<AssetFlow>, splits: List<Split>): LocalDate? {
        var found: LocalDate? = null
        walk(flows, splits, LocalDate.MAX) { date, q ->
            if (found == null && q.signum() < 0) found = date
        }
        return found
    }

    /** [quantity] bought on [after] rescaled by the splits with after < date ≤ [through]. */
    fun adjust(quantity: BigDecimal, splits: List<Split>, after: LocalDate, through: LocalDate): BigDecimal =
        splits.filter { it.date.isAfter(after) && !it.date.isAfter(through) }
            .sortedBy { it.date }
            .fold(quantity) { q, split -> applySplit(q, split) }

    /** Value of [quantity] units at [unitPrice] (sub-cent prices such as 0.0000041 kept), rounded once. */
    fun cost(quantity: BigDecimal, unitPrice: BigDecimal, currency: Currency): Money =
        Money.of(quantity.multiply(unitPrice), currency)

    private fun applySplit(q: BigDecimal, split: Split): BigDecimal =
        q.multiply(split.numerator).divide(split.denominator, SCALE, RoundingMode.HALF_EVEN)

    /** Visits each distinct date up to [at] in order, splits first then flows, with the quantity at the day's end. */
    private inline fun walk(flows: List<AssetFlow>, splits: List<Split>, at: LocalDate, visit: (LocalDate, BigDecimal) -> Unit) {
        val flowsByDate = flows.groupBy { it.date }
        val splitsByDate = splits.groupBy { it.date }
        var quantity = BigDecimal.ZERO
        (flowsByDate.keys + splitsByDate.keys).filter { !it.isAfter(at) }.sorted().forEach { date ->
            splitsByDate[date].orEmpty().forEach { quantity = applySplit(quantity, it) }
            flowsByDate[date].orEmpty().forEach { flow ->
                val delta = flow.quantityDelta ?: BigDecimal.ZERO
                quantity = if (flow.kind.inflow) quantity + delta else quantity - delta
            }
            visit(date, quantity)
        }
    }
}
