package io.github.luannamorim.myfinance.ui.feature

import io.github.luannamorim.myfinance.domain.cash.CashLedger
import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.market.MarketData
import io.github.luannamorim.myfinance.domain.market.PricePoint
import io.github.luannamorim.myfinance.domain.market.SymbolHistory
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
import io.github.luannamorim.myfinance.testutil.rate
import io.github.luannamorim.myfinance.testutil.transfer
import io.github.luannamorim.myfinance.testutil.usd
import io.github.luannamorim.myfinance.ui.common.OccurrenceRow
import io.github.luannamorim.myfinance.ui.feature.asset.assetDetailState
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

    private val ptax = ExchangeRates(listOf(rate("2026-11-12", "5.6143")))

    @Test
    fun pendingRow_inOtherCurrency_isConverted() {
        val o = pending(EntryKind.EXPENSE, brl("250.00"), "2026-11-20", category = 20, name = "Electricity")
        val row = OccurrenceRow.of(o, emptyMap(), today, Currency.USD, Converter(ptax))
        assertEquals(usd("44.53"), row.amount) // 250,00 / 5,6143 = 44,529…
        assertEquals(brl("250.00"), row.original)
    }

    @Test
    fun overduePendingRow_usesTheLatestRate_likeTheTotals() {
        val rates = ExchangeRates(listOf(rate("2026-10-01", "5.0000"), rate("2026-11-12", "6.0000")))
        val o = pending(EntryKind.EXPENSE, usd("100.00"), "2026-10-25", category = 20, name = "Hosting")
        val row = OccurrenceRow.of(o, emptyMap(), today, Currency.BRL, Converter(rates))
        // Projected counts a pending item at the latest rate (6,00), not at its due date's (5,00).
        assertEquals(brl("600.00"), row.amount)
        assertEquals(usd("100.00"), row.original)
    }

    @Test
    fun row_inDisplayCurrency_hasNoOriginal() {
        val o = pending(EntryKind.EXPENSE, brl("250.00"), "2026-11-20", category = 20, name = "Electricity")
        val row = OccurrenceRow.of(o, emptyMap(), today, Currency.BRL, Converter(ptax))
        assertEquals(brl("250.00"), row.amount)
        assertNull(row.original)
    }

    @Test
    fun row_withoutRate_keepsItsAmount() {
        val o = pending(EntryKind.INCOME, usd("1000.00"), "2026-11-25", category = 20, name = "Website")
        val row = OccurrenceRow.of(o, emptyMap(), today, Currency.BRL, Converter(ExchangeRates.EMPTY))
        assertEquals(usd("1000.00"), row.amount)
        assertNull(row.original)
    }

    @Test
    fun confirmedRow_convertsAtThePaymentDate() {
        val rates = ExchangeRates(listOf(rate("2026-11-01", "5.0000"), rate("2026-11-10", "6.0000")))
        val o = confirmed(EntryKind.INCOME, usd("100.00"), "2026-11-05", on = "2026-11-10", category = 20, name = "Website")
        val row = OccurrenceRow.of(o, emptyMap(), today, Currency.BRL, Converter(rates))
        assertEquals(brl("600.00"), row.amount) // the 10/11 rate, not the 05/11 due date's
        assertEquals(usd("100.00"), row.original)
    }

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

    @Test
    fun cashFlowResult_isIncomeMinusExpenses_withoutTransfers() {
        val withIncome = ledger.copy(
            occurrences = ledger.occurrences + listOf(
                confirmed(EntryKind.INCOME, brl("9000.00"), "2026-11-05", category = 10),
                confirmed(EntryKind.EXPENSE, brl("50.00"), "2026-11-02", category = 20),
            ),
        )
        val s = cashFlowState(withIncome, categories, ExchangeRates.EMPTY, Currency.BRL, YearMonth.of(2026, 11), today)
        // 9.000 − (2.500 pending + 50 paid) = 6.450; the R$ 1.000 transfer and October's overdue bill don't count.
        assertEquals(brl("6450.00"), s.result!!.money)
        // Received − paid: 9.000 − 50 = 8.950
        assertEquals(brl("8950.00"), s.resultSoFar!!.money)
    }

    @Test
    fun assetDetail_seriesUsesQuote() {
        val aapl = Asset(5, "Apple", 1, Currency.USD, null, null, d("2026-11-02"), null, null, null, "AAPL")
        val data = portfolio.copy(
            assets = listOf(aapl),
            flows = mapOf(5L to listOf(AssetFlow(7, 5, FlowKind.INITIAL, d("2026-11-02"), usd("1000.00"), BigDecimal("2.5"), null))),
            market = MarketData(mapOf("AAPL" to SymbolHistory(listOf(PricePoint(d("2026-11-10"), BigDecimal("347.19"))), emptyList()))),
        )
        val state = assetDetailState(data, 5, ExchangeRates.EMPTY, Currency.USD, today) { null }
        assertEquals(usd("867.98"), state.series.last().value.money)
    }
}
