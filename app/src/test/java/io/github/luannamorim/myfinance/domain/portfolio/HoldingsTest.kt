package io.github.luannamorim.myfinance.domain.portfolio

import io.github.luannamorim.myfinance.domain.market.Split
import io.github.luannamorim.myfinance.domain.model.AssetFlow
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.usd
import java.math.BigDecimal
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class HoldingsTest {

    private fun flow(kind: FlowKind, date: String, qty: String?, id: Long = 1) =
        AssetFlow(id, 1, kind, d(date), usd("1.00"), qty?.let { BigDecimal(it) }, null)

    private fun split(date: String, n: Int, den: Int) = Split(d(date), BigDecimal(n), BigDecimal(den))

    private fun assertQty(expected: String, actual: BigDecimal?) {
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))
    }

    @Test fun quantityAt_forwardSplit() {
        val flows = listOf(
            flow(FlowKind.INITIAL, "2024-06-03", "2", 1),
            flow(FlowKind.CONTRIBUTION, "2024-06-10", "5", 2),
            flow(FlowKind.PARTIAL_DISPOSAL, "2024-06-12", "4", 3),
        )
        val splits = listOf(split("2024-06-10", 10, 1))
        assertQty("2", Holdings.quantityAt(flows, splits, d("2024-06-09")))
        assertQty("25", Holdings.quantityAt(flows, splits, d("2024-06-10")))
        assertQty("21", Holdings.quantityAt(flows, splits, d("2024-06-12")))
    }

    @Test fun quantityAt_reverseSplit() {
        val flows = listOf(flow(FlowKind.INITIAL, "2021-07-01", "100"))
        assertQty("12.5", Holdings.quantityAt(flows, listOf(split("2021-08-02", 1, 8)), d("2021-08-02")))
    }

    @Test fun quantityAt_nonTerminatingSplit() {
        val flows = listOf(flow(FlowKind.INITIAL, "2021-07-01", "10"))
        assertQty("3.3333333333", Holdings.quantityAt(flows, listOf(split("2021-07-02", 1, 3)), d("2021-07-02")))
    }

    @Test fun quantityAt_flowWithoutQuantity_isNull() {
        val flows = listOf(flow(FlowKind.INITIAL, "2021-07-01", null))
        assertNull(Holdings.quantityAt(flows, emptyList(), d("2021-07-02")))
    }

    @Test fun fullDisposal_leavesZero() {
        val flows = listOf(flow(FlowKind.INITIAL, "2026-11-02", "2.5", 1), flow(FlowKind.FULL_DISPOSAL, "2026-11-10", "2.5", 2))
        assertQty("0", Holdings.quantityAt(flows, emptyList(), d("2026-11-23")))
    }

    @Test fun firstShortfall() {
        val over = listOf(flow(FlowKind.INITIAL, "2026-11-02", "2", 1), flow(FlowKind.PARTIAL_DISPOSAL, "2026-11-10", "3", 2))
        assertEquals(d("2026-11-10"), Holdings.firstShortfall(over, emptyList()))
        val even = listOf(flow(FlowKind.INITIAL, "2026-11-02", "2", 1), flow(FlowKind.PARTIAL_DISPOSAL, "2026-11-10", "2", 2))
        assertNull(Holdings.firstShortfall(even, emptyList()))
    }

    @Test fun sameDayBuyAndSell_noFalseShortfall() {
        val flows = listOf(
            flow(FlowKind.INITIAL, "2026-11-02", "1", 1),
            flow(FlowKind.PARTIAL_DISPOSAL, "2026-11-05", "2", 3),
            flow(FlowKind.CONTRIBUTION, "2026-11-05", "2", 4),
        )
        assertNull(Holdings.firstShortfall(flows, emptyList()))
    }

    @Test fun adjust_appliesOnlySplitsAfterPurchase() {
        val splits = listOf(split("2024-06-10", 10, 1))
        assertQty("100", Holdings.adjust(BigDecimal(10), splits, d("2024-06-03"), d("2026-11-23")))
        assertQty("10", Holdings.adjust(BigDecimal(10), splits, d("2024-06-10"), d("2026-11-23")))
    }

    @Test fun cost_roundsOnce() {
        assertEquals(usd("50.02"), Holdings.cost(BigDecimal("0.5"), BigDecimal("100.05"), Currency.USD))
        assertEquals(usd("1212.80"), Holdings.cost(BigDecimal(10), BigDecimal("121.28"), Currency.USD))
        assertEquals(usd("205.00"), Holdings.cost(BigDecimal(50_000_000), BigDecimal("0.0000041"), Currency.USD))
        // Typed values whose product does not fit in money: no total instead of a crash.
        assertEquals(null, Holdings.cost(BigDecimal("999999999999"), BigDecimal("100000"), Currency.USD))
    }
}
