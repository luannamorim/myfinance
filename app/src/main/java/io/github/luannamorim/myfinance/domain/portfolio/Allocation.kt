package io.github.luannamorim.myfinance.domain.portfolio

import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** One category's slice of the portfolio. [share] is 0..1 at scale 4. */
data class Slice(val categoryId: Long, val value: Money, val share: BigDecimal, val incomplete: Boolean = false)

object Allocation {
    private const val SHARE_SCALE = 4

    /**
     * Value by category ÷ total value of open assets, largest first. Shares sum to exactly 1: the rounding
     * remainder goes to the largest slice. Empty when there is no value.
     */
    fun of(data: PortfolioData, at: LocalDate, to: Currency, converter: Converter, group: AssetGroup? = null): List<Slice> {
        val byCategory = linkedMapOf<Long, Pair<BigDecimal, Boolean>>()
        data.assets.filter { group == null || data.groupOf(it) == group }.forEach { asset ->
            val s = PortfolioCalculator.snapshot(asset, data.flowsOf(asset), data.valuationsOf(asset), at) ?: return@forEach
            if (s.closed) return@forEach
            val converted = converter.exact(s.value, to, at)
            val (sum, incomplete) = byCategory[asset.categoryId] ?: (BigDecimal.ZERO to false)
            byCategory[asset.categoryId] = if (converted == null) sum to true else (sum + converted) to incomplete
        }
        val total = byCategory.values.fold(BigDecimal.ZERO) { acc, (v, _) -> acc + v }
        if (total.signum() <= 0) return emptyList()
        val slices = byCategory.map { (id, pair) ->
            Slice(id, Money.of(pair.first, to), pair.first.divide(total, SHARE_SCALE, RoundingMode.HALF_EVEN), pair.second)
        }.filter { it.value.minor > 0 }.sortedByDescending { it.value.minor }
        if (slices.isEmpty()) return slices
        val remainder = BigDecimal.ONE - slices.fold(BigDecimal.ZERO) { acc, s -> acc + s.share }
        return listOf(slices.first().copy(share = slices.first().share + remainder)) + slices.drop(1)
    }
}
