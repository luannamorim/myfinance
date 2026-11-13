package io.github.luannamorim.myfinance.ui.feature.cashflow

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.domain.cash.CashLedger
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.domain.model.Category
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind.EXPENSE
import io.github.luannamorim.myfinance.domain.model.EntryKind.INCOME
import io.github.luannamorim.myfinance.testutil.PHONE_TALL
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.confirmed
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.opening
import io.github.luannamorim.myfinance.testutil.pending
import io.github.luannamorim.myfinance.testutil.rate
import io.github.luannamorim.myfinance.testutil.snapBothThemes
import io.github.luannamorim.myfinance.testutil.usd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** Cash Flow dashboard state, built from a hand-computed ledger (same numbers as CashCalculatorTest). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = PHONE_TALL)
class CashFlowTest {

    @get:Rule val compose = createComposeRule()

    private val today = d("2026-11-12")
    private val categories = listOf(
        Category(10, null, INCOME, "work", "income.salary", false, 0),
        Category(20, null, EXPENSE, "key", "expense.rent", false, 1),
        Category(30, null, EXPENSE, "shopping_basket", "expense.groceries", false, 2),
        Category(40, null, EXPENSE, "bolt", "expense.electricity", false, 3),
        Category(50, null, EXPENSE, "wifi", "expense.internet", false, 4),
        Category(60, null, INCOME, "laptop", "income.freelance", false, 5),
    )
    private val ledger = CashLedger(
        adjustments = listOf(opening(brl("10000.00"), "2026-11-01")),
        occurrences = listOf(
            confirmed(INCOME, brl("9000.00"), "2026-11-05", category = 10, name = "Salary"),
            confirmed(EXPENSE, brl("2500.00"), "2026-11-10", actual = brl("2550.00"), category = 20, name = "Rent"),
            confirmed(EXPENSE, brl("312.40"), "2026-11-12", category = 30, name = "Groceries"),
            pending(EXPENSE, brl("250.00"), "2026-11-20", category = 40, name = "Electricity"),
            pending(EXPENSE, brl("120.00"), "2026-10-25", category = 50, name = "Internet"),
            pending(INCOME, brl("1500.00"), "2026-11-25", category = 60, name = "Website"),
            pending(EXPENSE, brl("2500.00"), "2026-12-10", category = 20, name = "Rent"),
            pending(INCOME, brl("9000.00"), "2026-12-05", category = 10, name = "Salary"),
        ),
    )

    private fun state(month: YearMonth = YearMonth.of(2026, 11)) =
        cashFlowState(ledger, categories, ExchangeRates.EMPTY, Currency.BRL, month, today)

    @Test
    fun currentMonth_figures() {
        val s = state()
        assertEquals(Total(brl("16137.60")), s.balance)
        // 16.137,60 − 250 − 120 + 1.500 = 17.267,60
        assertEquals(Total(brl("17267.60")), s.projectedEnd)
        assertEquals(listOf("Internet"), s.overdue.map { it.name })
        assertEquals(listOf("Electricity"), s.pendingBills.map { it.name })
        assertEquals(listOf("Website"), s.upcomingIncome.map { it.name })
        // Next month: 17.267,60 + 9.000 − 2.500 = 23.767,60
        assertEquals(Total(brl("23767.60")), s.nextMonthProjected)
        assertEquals(brl("9000.00"), s.nextMonth!!.expectedIncome.money)
        assertFalse(s.usesConversion)
    }

    @Test
    fun pastMonth_showsMonthEndBalance_andNoOverdueList() {
        val s = state(YearMonth.of(2026, 10))
        assertFalse(s.balanceIsToday)
        assertEquals(Total(brl("0.00")), s.balance)
        assertTrue(s.overdue.isEmpty())
    }

    @Test
    fun usdItem_needsARate_andIsFlagged() {
        val withUsd = ledger.copy(occurrences = ledger.occurrences + pending(EXPENSE, usd("20.00"), "2026-11-28", category = 40, name = "Cloud"))
        val noRate = cashFlowState(withUsd, categories, ExchangeRates.EMPTY, Currency.BRL, YearMonth.of(2026, 11), today)
        assertTrue(noRate.usesConversion)
        assertTrue(noRate.projectedEnd!!.incomplete)
        val rated = cashFlowState(withUsd, categories, ExchangeRates(listOf(rate("2026-11-12", "5.6143"))), Currency.BRL, YearMonth.of(2026, 11), today)
        // 17.267,60 − 20 × 5,6143 (112,29) = 17.155,31
        assertEquals(Total(brl("17155.31")), rated.projectedEnd)
    }

    @Test
    fun charts_useCalculatorTotals() {
        val charts = cashFlowCharts(ledger, categories, ExchangeRates.EMPTY, Currency.BRL, YearMonth.of(2026, 11), today)
        val nov = charts.incomeVsExpenses.last()
        assertEquals(YearMonth.of(2026, 11), nov.first)
        assertEquals(brl("10500.00"), nov.second) // 9.000 received + 1.500 pending
        assertEquals(brl("3112.40"), nov.third)
        assertEquals(listOf(20L, 30L, 40L), charts.expensesByCategory.map { it.category!!.id })
        assertEquals(listOf(YearMonth.of(2026, 11)), charts.balance.map { it.first })
    }

    @Test
    fun screenshots() {
        compose.snapBothThemes("cashflow") { CashFlowContent(state(), CashFlowActions()) }
    }

    @Test
    fun screenshot_empty() {
        compose.snapBothThemes("cashflow_empty") {
            CashFlowContent(cashFlowState(CashLedger(), categories, ExchangeRates.EMPTY, Currency.BRL, YearMonth.of(2026, 11), today), CashFlowActions())
        }
    }

    @Test
    fun screenshot_charts() {
        val more = ledger.copy(
            occurrences = ledger.occurrences + listOf(
                confirmed(INCOME, brl("8800.00"), "2026-10-05", category = 10),
                confirmed(EXPENSE, brl("2500.00"), "2026-10-10", category = 20),
                confirmed(EXPENSE, brl("640.00"), "2026-10-18", category = 30),
                confirmed(INCOME, brl("8800.00"), "2026-09-05", category = 10),
                confirmed(EXPENSE, brl("3900.00"), "2026-09-10", category = 20),
            ),
            adjustments = listOf(opening(brl("4000.00"), "2026-09-01")),
        )
        compose.snapBothThemes("cashflow_charts") {
            CashFlowChartsContent(cashFlowCharts(more, categories, ExchangeRates.EMPTY, Currency.BRL, YearMonth.of(2026, 11), today), {}, {})
        }
    }
}
