package io.github.luannamorim.myfinance.ui.feature.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.domain.cash.CashLedger
import io.github.luannamorim.myfinance.domain.cash.HistoryFilter
import io.github.luannamorim.myfinance.domain.cash.HistoryProgress
import io.github.luannamorim.myfinance.domain.model.Category
import io.github.luannamorim.myfinance.domain.model.EntryKind.EXPENSE
import io.github.luannamorim.myfinance.domain.model.EntryKind.INCOME
import io.github.luannamorim.myfinance.testutil.PHONE_TALL
import io.github.luannamorim.myfinance.testutil.adjustment
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.rate
import io.github.luannamorim.myfinance.testutil.usd
import io.github.luannamorim.myfinance.testutil.confirmed
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.pending
import io.github.luannamorim.myfinance.testutil.snapBothThemes
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = PHONE_TALL)
class HistoryTest {

    @get:Rule val compose = createComposeRule()

    private val today = d("2026-11-12")
    private val categories = listOf(
        Category(10, null, INCOME, "work", "income.salary", false, 0),
        Category(20, null, EXPENSE, "key", "expense.rent", false, 1),
        Category(30, null, EXPENSE, "shopping_basket", "expense.groceries", false, 2),
    )
    private val ledger = CashLedger(
        occurrences = listOf(
            confirmed(INCOME, brl("9000.00"), "2026-11-05", category = 10, name = "Salary"),
            confirmed(EXPENSE, brl("2500.00"), "2026-11-10", actual = brl("2550.00"), category = 20, name = "Rent"),
            confirmed(EXPENSE, brl("312.40"), "2026-11-12", category = 30, name = "Groceries"),
            pending(EXPENSE, brl("2500.00"), "2026-12-10", category = 20, name = "Rent"),
            confirmed(EXPENSE, brl("2500.00"), "2026-10-10", category = 20, name = "Rent"),
        ),
        adjustments = listOf(adjustment(brl("-37.60"), "2026-11-11")),
    )

    private val nov = YearMonth.of(2026, 11)

    @Test
    fun searchUsesLocalizedCategoryNames_withinTheMonth() {
        val s = historyState(ledger, categories, HistoryFilter(nov, query = "aluguel"), mapOf(20L to "Aluguel"), today)
        // November's rent only: the October and December ones are in their own months.
        assertEquals(listOf("Rent"), s.items.filterIsInstance<HistoryItem.Transaction>().map { it.row.name })
        assertEquals(1, s.items.size)
    }

    @Test
    fun displayInUsd_convertsTheRows_atTheirPaymentDate() {
        val rates = ExchangeRates(listOf(rate("2026-11-01", "5.0000")))
        val s = historyState(ledger, categories, HistoryFilter(nov), emptyMap(), today, Currency.USD, rates)
        val groceries = s.items.filterIsInstance<HistoryItem.Transaction>().first { it.row.name == "Groceries" }.row
        assertEquals(usd("62.48"), groceries.amount) // R$ 312,40 / 5,00
        assertEquals(brl("312.40"), groceries.original)
    }

    @Test
    fun screenshot() {
        val state = historyState(ledger, categories, HistoryFilter(nov), emptyMap(), today)
        compose.snapBothThemes("history") { HistoryContent(state, {}, {}) }
    }

    @Test
    fun screenshot_done() {
        val state = historyState(ledger, categories, HistoryFilter(nov, progress = HistoryProgress.DONE), emptyMap(), today)
        compose.snapBothThemes("history_done") { HistoryContent(state, {}, {}) }
    }

    @Test
    fun opensOnAMonth_andTheSwitcherMovesIt() {
        var filter by mutableStateOf(HistoryFilter(nov))
        compose.setContent {
            FinanceTheme { HistoryContent(historyState(ledger, categories, filter, emptyMap(), today), {}, { t -> filter = t(filter) }) }
        }
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        // The switcher names the month: no section header, and the old filters are gone.
        compose.onNodeWithText("November 2026".uppercase()).assertDoesNotExist()
        compose.onNodeWithText("Categories and dates").assertDoesNotExist()
        compose.onNodeWithText("Groceries").assertIsDisplayed()

        compose.onNodeWithContentDescription("Previous month").performClick()
        assertEquals(YearMonth.of(2026, 10), filter.month)
        compose.onNodeWithText("Groceries").assertDoesNotExist()
        compose.onNodeWithText("Rent").assertIsDisplayed()
    }

    @Test
    fun openAndDone_oneAtATime_andATapAgainShowsAll() {
        var filter by mutableStateOf(HistoryFilter(nov))
        compose.setContent {
            FinanceTheme { HistoryContent(historyState(ledger, categories, filter, emptyMap(), today), {}, { t -> filter = t(filter) }) }
        }
        // November has nothing open: the December rent is next month's.
        compose.onNodeWithText("Open").performClick()
        assertEquals(HistoryProgress.OPEN, filter.progress)
        compose.onNodeWithText("Nothing found this month.").assertIsDisplayed()

        compose.onNodeWithText("Done").performClick()
        assertEquals(HistoryProgress.DONE, filter.progress)
        compose.onNodeWithText("Groceries").assertIsDisplayed()
        compose.onNodeWithText("Balance adjustment").assertIsDisplayed()

        compose.onNodeWithText("Done").performClick()
        assertNull(filter.progress)
    }

    @Test
    fun emptyMonth_saysSo() {
        val state = historyState(ledger, categories, HistoryFilter(YearMonth.of(2026, 9)), emptyMap(), today)
        compose.setContent { FinanceTheme { HistoryContent(state, {}, {}) } }
        compose.onNodeWithText("No transactions this month.").assertIsDisplayed()
    }

    @Test
    fun showsRowsAndAdjustment() {
        val state = historyState(ledger, categories, HistoryFilter(nov), emptyMap(), today)
        compose.setContent { FinanceTheme { HistoryContent(state, {}, {}) } }
        compose.onNodeWithText("Balance adjustment").assertIsDisplayed()
        compose.onNodeWithText("Salary").assertIsDisplayed()
    }
}
