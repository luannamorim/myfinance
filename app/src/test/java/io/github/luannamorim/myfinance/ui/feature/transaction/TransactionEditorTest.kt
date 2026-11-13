package io.github.luannamorim.myfinance.ui.feature.transaction

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.data.repository.BackupTrigger
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.data.repository.TransactionDraft
import io.github.luannamorim.myfinance.domain.cash.RecurrenceRule
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.testutil.MainDispatcherRule
import io.github.luannamorim.myfinance.testutil.PHONE_TALL
import io.github.luannamorim.myfinance.testutil.awaitUntil
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.inMemoryDb
import io.github.luannamorim.myfinance.testutil.snapBothThemes
import io.github.luannamorim.myfinance.testutil.testSettings
import io.github.luannamorim.myfinance.ui.navigation.TransactionEditorRoute
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = PHONE_TALL)
class TransactionEditorTest {

    @get:Rule val main = MainDispatcherRule()
    @get:Rule val compose = createComposeRule()

    private val db = inMemoryDb(seed = true)
    private val time = FakeTimeSource()
    private val categories = CategoryRepository(db, time, BackupTrigger { })
    private val cash = CashRepository(db, time, BackupTrigger { })
    private val settings = testSettings()

    @After fun close() = db.close()

    private fun editor(route: TransactionEditorRoute = TransactionEditorRoute()) =
        TransactionEditorViewModel(SavedStateHandle(route = route), cash, categories, settings, time)

    private fun categoryId(seedKey: String) = runBlocking { db.categoryDao().getAll().first { it.seedKey == seedKey }.id }

    @Test
    fun missingFields_flagErrors_andSaveNothing() = runBlocking {
        val vm = editor()
        vm.state.awaitUntil { !it.loading }
        assertFalse(vm.save())
        val s = vm.state.value
        assertTrue(s.nameError && s.amountError && s.categoryError)
        assertTrue(cash.snapshot().occurrences.isEmpty())
    }

    @Test
    fun oneTimeExpenseToday_isSavedAsPaid() = runBlocking {
        val vm = editor()
        vm.state.awaitUntil { !it.loading && it.categories.isNotEmpty() }
        vm.setName("Groceries")
        vm.setDigits("31240")
        vm.setCategory(categoryId("expense.groceries"))
        assertTrue(vm.state.value.canSettle)
        assertTrue(vm.save())
        vm.state.awaitUntil { it.done }
        val row = cash.snapshot().occurrences.single()
        assertEquals(OccurrenceState.CONFIRMED, row.state)
        assertEquals(brl("312.40"), row.actual)
    }

    @Test
    fun monthlyIncome_createsTemplate_andHidesSettleToggle() = runBlocking {
        val vm = editor(TransactionEditorRoute(kind = EntryKind.INCOME))
        vm.state.awaitUntil { !it.loading && it.categories.isNotEmpty() }
        vm.setName("Salary")
        vm.setDigits("900000")
        vm.setCategory(categoryId("income.salary"))
        vm.setRepeat(RepeatChoice.Preset(RecurrenceRule.Preset.MONTHLY))
        assertFalse(vm.state.value.canSettle)
        assertTrue(vm.save())
        vm.state.awaitUntil { it.done }
        val ledger = cash.snapshot()
        assertEquals(1, ledger.templates.size)
        assertEquals(13, ledger.occurrences.size)
        assertTrue(ledger.occurrences.all { it.state == OccurrenceState.PENDING })
    }

    @Test
    fun editingTemplate_showsNextDue_andPassesItOnlyWhenChanged() = runBlocking {
        time.set("2026-11-12")
        val id = cash.create(TransactionDraft("Rent", categoryId("expense.rent"), EntryKind.EXPENSE, brl("2500.00"), d("2026-11-05"), RecurrenceRule.MONTHLY))
        val vm = editor(TransactionEditorRoute(templateId = id))
        val loaded = vm.state.awaitUntil { !it.loading }
        assertEquals(EditorMode.TEMPLATE, loaded.mode)
        assertEquals(d("2026-12-05"), loaded.nextDueDate)

        vm.setDigits("260000")
        assertTrue(vm.save())
        vm.state.awaitUntil { it.done }
        val t = cash.template(id)!!
        assertEquals(d("2026-11-05"), t.anchorDate)
        assertEquals(brl("2600.00"), cash.snapshot().occurrences.first { it.dueDate == d("2026-12-05") }.expected)
    }

    @Test
    fun createExpense_throughTheUi() {
        val vm = editor()
        compose.setContent { FinanceTheme { TransactionEditorScreen(onClose = {}, viewModel = vm) } }
        compose.waitUntil(5_000) { vm.state.value.categories.isNotEmpty() }
        compose.onNodeWithText("Name").performTextInput("Power bill")
        compose.onNodeWithText("Amount").performTextInput("28740")
        compose.onNodeWithText("Save").performClick()
        // Category is required: the error shows, then picking one lets it save.
        compose.waitUntil(5_000) { vm.state.value.categoryError }
        compose.onNodeWithText("Choose a category").assertExists()
        compose.onNodeWithText("Electricity").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { vm.state.value.done }
        val row = runBlocking { cash.snapshot().occurrences.single() }
        assertEquals("Power bill", row.name)
        assertEquals(categoryId("expense.electricity"), row.categoryId)
        assertEquals(brl("287.40"), row.expected)
    }

    @Test
    fun createIncome_throughTheUi() {
        val vm = editor()
        compose.setContent { FinanceTheme { TransactionEditorScreen(onClose = {}, viewModel = vm) } }
        compose.waitUntil(5_000) { vm.state.value.categories.isNotEmpty() }
        compose.onNodeWithText("Income").performClick()
        compose.waitUntil(5_000) { vm.state.value.categories.any { it.seedKey == "income.freelance" } }
        compose.onNodeWithText("Freelance").performClick()
        compose.onNodeWithText("Name").performTextInput("Website")
        compose.onNodeWithText("Amount").performTextInput("150000")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { vm.state.value.done }
        val row = runBlocking { cash.snapshot().occurrences.single() }
        assertEquals(EntryKind.INCOME, row.kind)
        assertEquals(OccurrenceState.CONFIRMED, row.state)
        assertEquals(brl("1500.00"), row.actual)
    }

    @Test
    fun screenshot_newRecurring() = compose.snapBothThemes("transaction_editor") {
        TransactionEditorContent(
            TransactionEditorUiState(
                loading = false, name = "Rent", digits = "250000", categoryId = 2,
                categories = runBlocking { categories.observe(EntryKind.EXPENSE).awaitUntil { it.isNotEmpty() } },
                date = d("2026-11-05"), today = d("2026-11-12"),
                repeat = RepeatChoice.Preset(RecurrenceRule.Preset.MONTHLY),
            ),
            TransactionEditorActions(),
        )
    }
}
