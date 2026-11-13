package io.github.luannamorim.myfinance.ui.feature.transaction

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.data.repository.BackupTrigger
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.data.repository.TransactionDraft
import io.github.luannamorim.myfinance.domain.cash.DisplayStatus
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
import io.github.luannamorim.myfinance.ui.navigation.OccurrenceDetailRoute
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = PHONE_TALL)
class OccurrenceDetailTest {

    @get:Rule val main = MainDispatcherRule()
    @get:Rule val compose = createComposeRule()

    private val db = inMemoryDb(seed = true)
    private val time = FakeTimeSource()
    private val categories = CategoryRepository(db, time, BackupTrigger { })
    private val cash = CashRepository(db, time, BackupTrigger { })

    @After fun close() = db.close()

    private fun category(seedKey: String) = runBlocking { db.categoryDao().getAll().first { it.seedKey == seedKey }.id }

    private fun detail(id: Long) = OccurrenceDetailViewModel(SavedStateHandle(route = OccurrenceDetailRoute(id)), cash, categories, time)

    /** Rent, monthly from 05/11/2026, R$ 2.500; returns the November occurrence id. */
    private fun rent(): Long = runBlocking {
        val template = cash.create(TransactionDraft("Rent", category("expense.rent"), EntryKind.EXPENSE, brl("2500.00"), d("2026-11-05"), RecurrenceRule.MONTHLY))
        cash.snapshot().occurrences.first { it.templateId == template && it.dueDate == d("2026-11-05") }.id
    }

    @Test
    fun confirmPayment_throughTheUi_keepsExpectedSeparate() {
        val id = rent()
        val vm = detail(id)
        compose.setContent { FinanceTheme { OccurrenceDetailScreen(onBack = {}, onEdit = {}, onEditRecurring = {}, viewModel = vm) } }
        compose.waitUntil(5_000) { vm.state.value.occurrence != null }
        assertEquals(DisplayStatus.OVERDUE, vm.state.value.status)

        compose.onNodeWithText("Confirm payment").performClick()
        compose.onNodeWithText("Amount paid").performTextClearance()
        compose.onNodeWithText("Amount paid").performTextInput("255000")
        compose.onNodeWithText("Note (optional)").performTextInput("late fee")
        compose.onNodeWithText("Confirm").performClick()

        compose.waitUntil(5_000) { vm.state.value.occurrence?.state == OccurrenceState.CONFIRMED }
        val paid = runBlocking { cash.occurrence(id)!! }
        assertEquals(brl("2500.00"), paid.expected)
        assertEquals(brl("2550.00"), paid.actual)
        assertEquals(d("2026-11-12"), paid.actualDate)
        assertEquals("late fee", paid.note)
        assertEquals(DisplayStatus.PAID, vm.state.value.status)
        assertEquals(brl("50.00"), vm.state.value.difference)
    }

    @Test
    fun confirmReceipt_throughTheUi() {
        val id = runBlocking {
            cash.create(TransactionDraft("Salary", category("income.salary"), EntryKind.INCOME, brl("9000.00"), d("2026-11-12"), null))
        }
        val vm = detail(id)
        compose.setContent { FinanceTheme { OccurrenceDetailScreen(onBack = {}, onEdit = {}, onEditRecurring = {}, viewModel = vm) } }
        compose.waitUntil(5_000) { vm.state.value.occurrence != null }
        compose.onNodeWithText("Confirm receipt").performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.waitUntil(5_000) { vm.state.value.status == DisplayStatus.RECEIVED }
        assertEquals(brl("9000.00"), runBlocking { cash.occurrence(id)!!.actual })
    }

    @Test
    fun markAsUnpaid_asksFirst_thenClearsActual() {
        val id = rent()
        runBlocking { cash.confirm(id, brl("2500.00"), d("2026-11-05"), null) }
        val vm = detail(id)
        compose.setContent { FinanceTheme { OccurrenceDetailScreen(onBack = {}, onEdit = {}, onEditRecurring = {}, viewModel = vm) } }
        compose.waitUntil(5_000) { vm.state.value.status == DisplayStatus.PAID }
        compose.onNodeWithText("Mark as unpaid").performClick()
        compose.onNodeWithText("Undo confirmation?").assertExists()
        compose.onAllNodesWithText("Mark as unpaid").onLast().performClick()
        compose.waitUntil(5_000) { vm.state.value.occurrence?.state == OccurrenceState.PENDING }
        assertNull(runBlocking { cash.occurrence(id)!!.actual })
    }

    @Test
    fun cancelAndRestore() = runBlocking {
        val id = rent()
        val vm = detail(id)
        vm.state.awaitUntil { it.occurrence != null }
        vm.cancel().join()
        vm.state.awaitUntil { it.status == DisplayStatus.CANCELLED }
        vm.restore().join()
        vm.state.awaitUntil { it.status == DisplayStatus.OVERDUE }
        Unit
    }

    @Test
    fun screenshots() {
        val id = rent()
        runBlocking { cash.confirm(id, brl("2550.00"), d("2026-11-10"), "late fee") }
        val state = runBlocking { detail(id).state.awaitUntil { it.occurrence != null } }
        compose.snapBothThemes("occurrence_detail_paid") { OccurrenceDetailContent(state, OccurrenceDetailActions()) }
        compose.onAllNodesWithText("Mark as unpaid").onFirst().assertExists()
    }
}
