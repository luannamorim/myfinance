package io.github.luannamorim.myfinance.domain.portfolio

import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.domain.model.Asset
import io.github.luannamorim.myfinance.domain.model.AssetCategory
import io.github.luannamorim.myfinance.domain.model.AssetFlow
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.Valuation
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Everything the portfolio calculations need, read in one snapshot. */
data class PortfolioData(
    val assets: List<Asset> = emptyList(),
    val flows: Map<Long, List<AssetFlow>> = emptyMap(),
    val valuations: Map<Long, List<Valuation>> = emptyMap(),
    val categories: List<AssetCategory> = emptyList(),
) {
    fun flowsOf(asset: Asset): List<AssetFlow> = flows[asset.id].orEmpty()
    fun valuationsOf(asset: Asset): List<Valuation> = valuations[asset.id].orEmpty()
    fun groupOf(asset: Asset): AssetGroup? = categories.firstOrNull { it.id == asset.categoryId }?.group
}

/** One asset at a date, in the asset's currency. */
data class AssetSnapshot(
    val asset: Asset,
    val contributed: Money,
    val withdrawn: Money,
    val netInvested: Money,
    /** Estimated value, never negative. */
    val value: Money,
    /** value − net invested (= value + withdrawn − contributed). */
    val gain: Money,
    /** gain ÷ contributed; null when nothing was contributed. */
    val simpleReturn: BigDecimal?,
    val lastValuationDate: LocalDate?,
    /** The value is the last valuation plus flows recorded after it. */
    val carriedForward: Boolean,
    val closed: Boolean,
)

/** One valuation of the history, with what had been invested at that date. */
data class ValuationRow(
    val valuation: Valuation,
    val netInvested: Money,
    val gain: Money,
    val simpleReturn: BigDecimal?,
)

/** An asset's figures converted to another currency: flows at their own dates, value at the snapshot date. */
data class ConvertedSnapshot(
    val contributed: Total,
    val withdrawn: Total,
    val value: Total,
    val gain: Total,
    val simpleReturn: BigDecimal?,
)

/** Dashboard totals over open assets, plus the realized gain of closed ones. */
data class PortfolioTotals(
    val value: Total,
    val invested: Total,
    val gain: Total,
    val simpleReturn: BigDecimal?,
    val lastValuationDate: LocalDate?,
    val realizedClosed: Total,
    val openCount: Int,
    val closedCount: Int,
)

/**
 * Portfolio calculations (docs/ARCHITECTURE.md, "Portfolio"). Simple return on a capital-recovery basis; values
 * between valuations carry the last valuation forward with the flows recorded since. Never interpolated.
 */
object PortfolioCalculator {

    private const val SCALE = 10

    fun firstRecordDate(flows: List<AssetFlow>, valuations: List<Valuation>): LocalDate? =
        (flows.map { it.date } + valuations.map { it.date }).minOrNull()

    fun isClosed(flows: List<AssetFlow>, at: LocalDate): Boolean =
        flows.any { it.kind == FlowKind.FULL_DISPOSAL && !it.date.isAfter(at) }

    private fun signed(flow: AssetFlow): Long = if (flow.kind.inflow) flow.amount.minor else -flow.amount.minor

    /** The asset at the end of [at]; null when it had no record yet. */
    fun snapshot(asset: Asset, flows: List<AssetFlow>, valuations: List<Valuation>, at: LocalDate): AssetSnapshot? {
        val first = firstRecordDate(flows, valuations) ?: return null
        if (at.isBefore(first)) return null
        val c = asset.currency
        val upTo = flows.filter { !it.date.isAfter(at) }
        val contributed = upTo.filter { it.kind.inflow }.sumOf { it.amount.minor }
        val withdrawn = upTo.filterNot { it.kind.inflow }.sumOf { it.amount.minor }
        val net = contributed - withdrawn
        val closed = isClosed(flows, at)
        val last = valuations.filter { !it.date.isAfter(at) }.maxByOrNull { it.date }
        val after = if (last == null) emptyList() else upTo.filter { it.date.isAfter(last.date) }
        val rawValue = when {
            closed -> 0L
            last != null -> last.value.minor + after.sumOf(::signed)
            else -> net
        }
        val value = rawValue.coerceAtLeast(0)
        val gain = value - net
        return AssetSnapshot(
            asset = asset,
            contributed = Money(contributed, c),
            withdrawn = Money(withdrawn, c),
            netInvested = Money(net, c),
            value = Money(value, c),
            gain = Money(gain, c),
            simpleReturn = ratio(gain, contributed),
            lastValuationDate = last?.date,
            carriedForward = !closed && after.isNotEmpty(),
            closed = closed,
        )
    }

    fun valueAt(asset: Asset, flows: List<AssetFlow>, valuations: List<Valuation>, at: LocalDate): Money? =
        snapshot(asset, flows, valuations, at)?.value

    /** Valuation history, oldest first, each row with what had been invested at that date. */
    fun valuationRows(asset: Asset, flows: List<AssetFlow>, valuations: List<Valuation>): List<ValuationRow> =
        valuations.sortedBy { it.date }.map { v ->
            val s = checkNotNull(snapshot(asset, flows, valuations, v.date))
            ValuationRow(v, s.netInvested, v.value - s.netInvested, ratio(v.value.minor - s.netInvested.minor, s.contributed.minor))
        }

    /** (value(end) − value(start)) − net flows in (start, end]. value(start) is 0 before the first record. */
    fun periodGain(asset: Asset, flows: List<AssetFlow>, valuations: List<Valuation>, start: LocalDate, end: LocalDate): Money? {
        val first = firstRecordDate(flows, valuations) ?: return null
        if (first.isAfter(end)) return null
        val endValue = valueAt(asset, flows, valuations, end)?.minor ?: 0
        val startValue = valueAt(asset, flows, valuations, start)?.minor ?: 0
        val netFlows = flows.filter { it.date.isAfter(start) && !it.date.isAfter(end) }.sumOf(::signed)
        return Money(endValue - startValue - netFlows, asset.currency)
    }

    /** Converts a snapshot: contributions and withdrawals at their own dates, value at [at]. */
    fun convertedSnapshot(snapshot: AssetSnapshot, flows: List<AssetFlow>, at: LocalDate, to: Currency, converter: Converter): ConvertedSnapshot {
        val upTo = flows.filter { !it.date.isAfter(at) }
        val contributed = converter.sum(upTo.filter { it.kind.inflow }.map { it.amount to it.date }, to)
        val withdrawn = converter.sum(upTo.filterNot { it.kind.inflow }.map { it.amount to it.date }, to)
        val value = converter.sum(listOf(snapshot.value to at), to)
        val gain = value + withdrawn - contributed
        val incomplete = gain.incomplete
        return ConvertedSnapshot(
            contributed = contributed,
            withdrawn = withdrawn,
            value = value,
            gain = gain,
            simpleReturn = if (incomplete) null else ratio(gain.money.minor, contributed.money.minor),
        )
    }

    fun totals(data: PortfolioData, at: LocalDate, to: Currency, converter: Converter, group: AssetGroup? = null): PortfolioTotals {
        var value = Total.zero(to)
        var invested = Total.zero(to)
        var contributed = Total.zero(to)
        // Return only over assets that received money: a granted or inherited asset (nothing contributed) has a
        // gain but no return, and would otherwise inflate the percentage.
        var gainWithContributions = Total.zero(to)
        var realized = Total.zero(to)
        var lastValuation: LocalDate? = null
        var open = 0
        var closed = 0
        data.assets.filter { group == null || data.groupOf(it) == group }.forEach { asset ->
            val flows = data.flowsOf(asset)
            val s = snapshot(asset, flows, data.valuationsOf(asset), at) ?: return@forEach
            val converted = convertedSnapshot(s, flows, at, to, converter)
            if (s.closed) {
                closed++
                realized += converted.gain
            } else {
                open++
                value += converted.value
                invested += converted.contributed - converted.withdrawn
                if (s.contributed.minor > 0) {
                    contributed += converted.contributed
                    gainWithContributions += converted.gain
                }
                s.lastValuationDate?.let { d -> if (lastValuation == null || d.isAfter(lastValuation)) lastValuation = d }
            }
        }
        val gain = value - invested
        return PortfolioTotals(
            value = value,
            invested = invested,
            gain = gain,
            simpleReturn = if (gainWithContributions.incomplete) null else ratio(gainWithContributions.money.minor, contributed.money.minor),
            lastValuationDate = lastValuation,
            realizedClosed = realized,
            openCount = open,
            closedCount = closed,
        )
    }

    /** a ÷ b at scale 10, or null when b is not positive. */
    fun ratio(a: Long, b: Long): BigDecimal? =
        if (b <= 0) null else BigDecimal.valueOf(a).divide(BigDecimal.valueOf(b), SCALE, RoundingMode.HALF_EVEN)
}
