package io.github.luannamorim.myfinance.domain.cash

import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.domain.model.Currency.BRL
import io.github.luannamorim.myfinance.domain.model.Currency.USD
import io.github.luannamorim.myfinance.domain.model.EntryKind.EXPENSE
import io.github.luannamorim.myfinance.domain.model.EntryKind.INCOME
import io.github.luannamorim.myfinance.domain.model.EntryKind.TRANSFER_IN
import io.github.luannamorim.myfinance.domain.model.EntryKind.TRANSFER_OUT
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.testutil.adjustment
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.confirmed
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.opening
import io.github.luannamorim.myfinance.testutil.pending
import io.github.luannamorim.myfinance.testutil.rate
import io.github.luannamorim.myfinance.testutil.transfer
import io.github.luannamorim.myfinance.testutil.usd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** Expected values are computed by hand in the comments. */
class CashCalculatorTest {

    private val today = d("2026-11-12")
    private val noRates = Converter(ExchangeRates.EMPTY)

    private val ledger = CashLedger(
        adjustments = listOf(opening(brl("10000.00"), "2026-11-01")),
        occurrences = listOf(
            confirmed(INCOME, brl("9000.00"), "2026-11-05", category = 10, name = "Salary"),
            confirmed(EXPENSE, brl("2500.00"), "2026-11-10", actual = brl("2550.00"), category = 20, name = "Rent"),
            confirmed(EXPENSE, brl("312.40"), "2026-11-12", category = 30, name = "Groceries"),
            pending(EXPENSE, brl("250.00"), "2026-11-20", category = 40, name = "Electricity"),
            pending(EXPENSE, brl("120.00"), "2026-10-25", category = 50, name = "Internet"),
            confirmed(EXPENSE, brl("80.00"), "2026-10-30", category = 30, name = "Before opening"),
            pending(EXPENSE, brl("999.00"), "2026-11-15", name = "Cancelled").copy(state = OccurrenceState.CANCELLED),
        ),
    )

    @Test
    fun balance_countsActualsSinceOpening() {
        // 10.000 + 9.000 − 2.550 − 312,40 = 16.137,60 (the 30/10 expense predates the opening balance)
        assertEquals(brl("16137.60"), CashCalculator.balance(BRL, today, ledger))
        // Before the salary: 10.000
        assertEquals(brl("10000.00"), CashCalculator.balance(BRL, d("2026-11-04"), ledger))
        // Before the opening: nothing
        assertEquals(brl("0.00"), CashCalculator.balance(BRL, d("2026-10-31"), ledger))
        assertEquals(usd("0.00"), CashCalculator.balance(USD, today, ledger))
    }

    @Test
    fun projected_addsPendingThroughDate_includingOverdue_once() {
        // 16.137,60 − 250 (electricity) − 120 (overdue internet) = 15.767,60; cancelled ignored
        assertEquals(brl("15767.60"), CashCalculator.projected(ledger, today, d("2026-11-30"))[BRL])
        // Through 15/11 the electricity (due 20/11) is not included yet: 16.137,60 − 120 = 16.017,60
        assertEquals(brl("16017.60"), CashCalculator.projected(ledger, today, d("2026-11-15"))[BRL])
    }

    @Test
    fun monthSummary_mixesActualAndExpected() {
        val nov = CashCalculator.monthSummary(ledger, YearMonth.of(2026, 11), today, BRL, noRates)
        assertEquals(Total(brl("9000.00")), nov.expectedIncome)
        assertEquals(Total(brl("9000.00")), nov.receivedIncome)
        // 2.550 (actual) + 312,40 + 250 (pending) = 3.112,40
        assertEquals(Total(brl("3112.40")), nov.expectedExpenses)
        assertEquals(Total(brl("2862.40")), nov.paidExpenses)
        assertEquals(Total(brl("120.00")), nov.overdueEarlier)
        assertEquals(1, nov.overdueEarlierCount)

        // Next month: this month's not-yet-due electricity is not "overdue from earlier months".
        val dec = CashCalculator.monthSummary(ledger, YearMonth.of(2026, 12), today, BRL, noRates)
        assertEquals(Total(brl("120.00")), dec.overdueEarlier)
        assertEquals(Total(brl("0.00")), dec.expectedExpenses)
    }

    @Test
    fun adjustments_countFromOpening() {
        val withAdjustment = ledger.copy(adjustments = ledger.adjustments + adjustment(brl("-137.60"), "2026-11-12"))
        assertEquals(brl("16000.00"), CashCalculator.balance(BRL, today, withAdjustment))
        // The day before, neither the groceries (12/11) nor the adjustment count: 10.000 + 9.000 − 2.550
        assertEquals(brl("16450.00"), CashCalculator.balance(BRL, d("2026-11-11"), withAdjustment))
    }

    @Test
    fun usdCash_isSeparate_andConsolidatesAtLatestRate() {
        val mixed = ledger.copy(
            adjustments = ledger.adjustments + opening(usd("500.00"), "2026-11-01"),
            occurrences = ledger.occurrences + confirmed(INCOME, usd("1000.00"), "2026-11-11", name = "Freelance"),
        )
        val available = CashCalculator.available(mixed, today)
        assertEquals(usd("1500.00"), available[USD])
        assertEquals(brl("16137.60"), available[BRL])
        val converter = Converter(ExchangeRates(listOf(rate("2026-11-12", "5.6143"))))
        // 16.137,60 + 1.500 × 5,6143 = 16.137,60 + 8.421,45 = 24.559,05
        assertEquals(Total(brl("24559.05")), CashCalculator.consolidate(available, BRL, converter))
        // Without any rate the USD part is missing and the total says so.
        val partial = CashCalculator.consolidate(available, BRL, noRates)
        assertTrue(partial.incomplete)
        assertEquals(brl("16137.60"), partial.money)
    }

    @Test
    fun payingUsdBillInBrl_affectsBrlCash() {
        val bill = confirmed(EXPENSE, usd("20.00"), "2026-11-12", actual = brl("115.00"))
        val l = ledger.copy(occurrences = ledger.occurrences + bill)
        assertEquals(brl("16022.60"), CashCalculator.balance(BRL, today, l))
        assertEquals(usd("0.00"), CashCalculator.balance(USD, today, l))
    }

    @Test
    fun byCategory_largestFirst() {
        val expenses = CashCalculator.byCategory(ledger, YearMonth.of(2026, 11), EXPENSE, BRL, noRates)
        assertEquals(listOf(20L, 30L, 40L), expenses.map { it.first })
        assertEquals(brl("2550.00"), expenses.first().second.money)
    }

    @Test
    fun balanceHistory_monthEnds() {
        val history = CashCalculator.balanceHistory(ledger, listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11)), BRL, noRates)
        assertEquals(brl("0.00"), history[0].second.money)
        assertEquals(brl("16137.60"), history[1].second.money)
        assertFalse(history[1].second.incomplete)
    }

    private val withTransfers = ledger.copy(
        occurrences = ledger.occurrences + listOf(
            transfer(TRANSFER_OUT, brl("3000.00"), "2026-11-11", on = "2026-11-11", name = "To Tesouro"),
            transfer(TRANSFER_IN, brl("500.00"), "2026-11-12", on = "2026-11-12", name = "From CDB"),
            transfer(TRANSFER_OUT, brl("1000.00"), "2026-11-25", name = "Monthly contribution"),
        ),
    )

    @Test
    fun transfers_moveCash_butAreNeitherIncomeNorExpense() {
        // 16.137,60 − 3.000 (to Tesouro) + 500 (from CDB) = 13.637,60
        assertEquals(brl("13637.60"), CashCalculator.balance(BRL, today, withTransfers))
        // Projection also takes the pending 1.000 contribution: 13.637,60 − 250 − 120 − 1.000 = 12.267,60
        assertEquals(brl("12267.60"), CashCalculator.projected(withTransfers, today, d("2026-11-30"))[BRL])

        val nov = CashCalculator.monthSummary(withTransfers, YearMonth.of(2026, 11), today, BRL, noRates)
        // Income and expenses are the same as without transfers.
        assertEquals(Total(brl("9000.00")), nov.expectedIncome)
        assertEquals(Total(brl("3112.40")), nov.expectedExpenses)
        assertEquals(Total(brl("2862.40")), nov.paidExpenses)
        // 3.000 done + 1.000 pending out; 500 in.
        assertEquals(Total(brl("4000.00")), nov.transfersOut)
        assertEquals(Total(brl("500.00")), nov.transfersIn)
        // Category charts ignore transfers (they have no category).
        assertEquals(listOf(20L, 30L, 40L), CashCalculator.byCategory(withTransfers, YearMonth.of(2026, 11), EXPENSE, BRL, noRates).map { it.first })
    }

    @Test
    fun transferStatus_isTransferred_onceDone() {
        assertEquals(DisplayStatus.TRANSFERRED, OccurrenceStatus.of(transfer(TRANSFER_OUT, brl("1.00"), "2026-11-10", on = "2026-11-10"), today))
        assertEquals(DisplayStatus.TRANSFERRED, OccurrenceStatus.of(transfer(TRANSFER_IN, brl("1.00"), "2026-11-10", on = "2026-11-10"), today))
        assertEquals(DisplayStatus.OVERDUE, OccurrenceStatus.of(transfer(TRANSFER_OUT, brl("1.00"), "2026-11-10"), today))
    }
}
