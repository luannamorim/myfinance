package io.github.luannamorim.myfinance.ui.feature

import io.github.luannamorim.myfinance.domain.cash.CashLedger
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.model.Asset
import io.github.luannamorim.myfinance.domain.model.AssetCategory
import io.github.luannamorim.myfinance.domain.model.AssetFlow
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Category
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioData
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.confirmed
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.opening
import io.github.luannamorim.myfinance.testutil.pending
import io.github.luannamorim.myfinance.testutil.transfer
import io.github.luannamorim.myfinance.ui.feature.cashflow.ChartSpan
import io.github.luannamorim.myfinance.ui.feature.cashflow.cashFlowCharts
import io.github.luannamorim.myfinance.ui.feature.cashflow.cashFlowState
import io.github.luannamorim.myfinance.ui.feature.overview.overviewState
import io.github.luannamorim.myfinance.ui.feature.portfolio.portfolioState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

/** Screen states built from domain data (the 1.1.0 changes). */
class StateBuildersTest {

    private val today = d("2026-11-12")
    private val categories = listOf(Category(20, null, EntryKind.EXPENSE, "key", "expense.rent", false, 0))
    private val ledger = CashLedger(
        adjustments = listOf(opening(brl("10000.00"), "2026-11-01")),
        occurrences = listOf(
            pending(EntryKind.EXPENSE, brl("120.00"), "2026-10-25", category = 20, name = "Overdue"),
            pending(EntryKind.EXPENSE, brl("2500.00"), "2026-11-20", category = 20, name = "This month"),
            pending(EntryKind.EXPENSE, brl("2500.00"), "2026-12-20", category = 20, name = "Next month"),
            transfer(EntryKind.TRANSFER_OUT, brl("1000.00"), "2026-11-25", name = "Contribution"),
        ),
    )
    private val tesouro = Asset(1, "Tesouro", 1, Currency.BRL, null, null, d("2026-01-10"), null, null, null)
    private val portfolio = PortfolioData(
        assets = listOf(tesouro),
        flows = mapOf(1L to listOf(AssetFlow(1, 1, FlowKind.INITIAL, d("2026-01-10"), brl("30000.00"), null, null))),
        valuations = emptyMap(),
        categories = listOf(AssetCategory(1, null, AssetGroup.INVESTMENT, "receipt_long", "asset.fixed_income", false, 0)),
    )

    @Test
    fun overviewUpcoming_isOverdueAndThisMonthOnly() {
        val s = overviewState(ledger, portfolio, categories, ExchangeRates.EMPTY, Currency.BRL, today)
        assertEquals(listOf("Overdue", "This month", "Contribution"), s.upcoming.map { it.name })
    }

    @Test
    fun portfolioShowsCash_forReference_onlyInAll() {
        val all = portfolioState(portfolio, ExchangeRates.EMPTY, Currency.BRL, null, null, today, ledger)
        assertEquals(brl("10000.00"), all.cash!!.money)
        // 10.000 / (10.000 + 30.000)
        assertEquals(BigDecimal("0.2500"), all.cashShare)
        // The portfolio figures don't include it.
        assertEquals(brl("30000.00"), all.totals!!.value.money)

        val investments = portfolioState(portfolio, ExchangeRates.EMPTY, Currency.BRL, AssetGroup.INVESTMENT, null, today, ledger)
        assertNull(investments.cash)
        // No cash data yet: nothing to show.
        assertNull(portfolioState(portfolio, ExchangeRates.EMPTY, Currency.BRL, null, null, today, CashLedger()).cash)
    }

    @Test
    fun charts_followTheSpan_andTheBalanceStartsAtTheFirstRecord() {
        val m3 = cashFlowCharts(ledger, categories, ExchangeRates.EMPTY, Currency.BRL, YearMonth.of(2026, 11), today, ChartSpan.M3)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), m3.incomeVsExpenses.map { it.first })
        assertEquals(12, cashFlowCharts(ledger, categories, ExchangeRates.EMPTY, Currency.BRL, YearMonth.of(2026, 11), today, ChartSpan.Y1).incomeVsExpenses.size)
        // Opening on 01/11, then today: a line already in the first month.
        assertEquals(listOf(d("2026-11-01"), today), m3.balance.map { it.first })
    }

    @Test
    fun cashFlow_listsPendingTransfers_apartFromBills() {
        val s = cashFlowState(ledger.copy(occurrences = ledger.occurrences + confirmed(EntryKind.EXPENSE, brl("50.00"), "2026-11-02", category = 20)), categories, ExchangeRates.EMPTY, Currency.BRL, YearMonth.of(2026, 11), today)
        assertEquals(listOf("Contribution"), s.pendingTransfers.map { it.name })
        assertEquals(listOf("This month"), s.pendingBills.map { it.name })
        assertEquals(brl("1000.00"), s.summary!!.transfersOut.money)
    }
}
