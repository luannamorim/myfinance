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
import io.github.luannamorim.myfinance.domain.model.Category
import io.github.luannamorim.myfinance.domain.model.EntryKind.EXPENSE
import io.github.luannamorim.myfinance.domain.model.EntryKind.INCOME
import io.github.luannamorim.myfinance.testutil.PHONE_TALL
import io.github.luannamorim.myfinance.testutil.adjustment
import io.github.luannamorim.myfinance.testutil.brl
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

    @Test
    fun searchUsesLocalizedCategoryNames() {
        val s = historyState(ledger, categories, HistoryFilter(query = "aluguel"), mapOf(20L to "Aluguel"), today)
        assertEquals(3, s.months.sumOf { it.second.size })
        assertEquals(listOf(YearMonth.of(2026, 12), YearMonth.of(2026, 11), YearMonth.of(2026, 10)), s.months.map { it.first })
    }

    @Test
    fun screenshot_grouped() {
        val state = historyState(ledger, categories, HistoryFilter(), emptyMap(), today)
        compose.snapBothThemes("history") { HistoryContent(state, {}, {}, {}) }
    }

    @Test
    fun monthChip_startsAtTheCurrentMonth_andTheSwitcherMovesIt() {
        var filter by mutableStateOf(HistoryFilter())
        compose.setContent {
            FinanceTheme { HistoryContent(historyState(ledger, categories, filter, emptyMap(), today), {}, { t -> filter = t(filter) }, {}) }
        }
        compose.onNodeWithText("Month").performClick()
        assertEquals(YearMonth.of(2026, 11), filter.month)
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        // The switcher names the month, so the section header is not repeated.
        compose.onNodeWithText("November 2026".uppercase()).assertDoesNotExist()
        compose.onNodeWithText("Groceries").assertIsDisplayed()

        compose.onNodeWithContentDescription("Previous month").performClick()
        assertEquals(YearMonth.of(2026, 10), filter.month)
        compose.onNodeWithText("Groceries").assertDoesNotExist()
        compose.onNodeWithText("Rent").assertIsDisplayed()

        compose.onNodeWithText("Month").performClick()
        assertNull(filter.month)
        compose.onNodeWithText("Groceries").assertIsDisplayed()
    }

    @Test
    fun screenshot_monthFilter() {
        val state = historyState(ledger, categories, HistoryFilter(month = YearMonth.of(2026, 11)), emptyMap(), today)
        compose.snapBothThemes("history_month") { HistoryContent(state, {}, {}, {}) }
    }

    @Test
    fun showsRowsAndAdjustment() {
        val state = historyState(ledger, categories, HistoryFilter(), emptyMap(), today)
        compose.setContent { FinanceTheme { HistoryContent(state, {}, {}, {}) } }
        compose.onNodeWithText("Balance adjustment").assertIsDisplayed()
        compose.onNodeWithText("November 2026".uppercase()).assertIsDisplayed()
    }
}
