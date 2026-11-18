package io.github.luannamorim.myfinance.ui.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.data.repository.BackupTrigger
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.domain.cash.CashCalculator
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.testutil.MainDispatcherRule
import io.github.luannamorim.myfinance.testutil.PHONE_TALL
import io.github.luannamorim.myfinance.testutil.awaitUntil
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.inMemoryDb
import io.github.luannamorim.myfinance.testutil.opening
import io.github.luannamorim.myfinance.testutil.usd
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.testutil.snapBothThemes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = PHONE_TALL)
class BalanceViewModelTest {

    @get:Rule val main = MainDispatcherRule()
    @get:Rule val compose = createComposeRule()

    private val db = inMemoryDb(seed = true)
    private val time = FakeTimeSource()
    private val cash = CashRepository(db, time, BackupTrigger { })

    // The in-memory database is not closed: ViewModel collectors may still be querying it when the test ends.

    @Test
    fun opening_isSavedOnce_andReplaced() = runBlocking {
        val vm = BalanceViewModel(cash, time)
        vm.state.awaitUntil { !it.loading }
        vm.editOpening(Currency.BRL) { it.copy(digits = "1000000", date = d("2026-11-01")) }
        vm.saveOpening(Currency.BRL)
        vm.state.awaitUntil { it.openings[Currency.BRL] != null }
        vm.editOpening(Currency.BRL) { it.copy(digits = "1200000") }
        vm.saveOpening(Currency.BRL)
        val s = vm.state.awaitUntil { it.openings[Currency.BRL]?.amount == brl("12000.00") }
        assertEquals(1, cash.snapshot().adjustments.size)
        assertEquals(brl("12000.00"), s.balances[Currency.BRL])
    }

    @Test
    fun opening_withoutAnAmount_isNotSaved() = runBlocking {
        val vm = BalanceViewModel(cash, time)
        vm.state.awaitUntil { !it.loading }
        // The US$ form is untouched (it reads 0,00): Save does nothing instead of storing a US$ 0,00 opening dated today.
        vm.saveOpening(Currency.USD)
        assertTrue(cash.snapshot().adjustments.isEmpty())
        assertTrue(!vm.state.value.openingForms.getValue(Currency.USD).canSave)
    }

    @Test
    fun opening_canBeRemoved_unlessItsCurrencyHasAdjustments() = runBlocking {
        cash.setOpening(usd("0.00"), d("2026-11-12"))
        cash.setOpening(brl("1000.00"), d("2026-11-01"))
        cash.addAdjustment(brl("900.00"), d("2026-11-10"), null)
        val vm = BalanceViewModel(cash, time)
        vm.state.awaitUntil { it.openings[Currency.USD] != null }

        vm.removeOpening(Currency.USD)
        val removed = vm.state.awaitUntil { it.openings[Currency.USD] == null }
        assertEquals("", removed.openingForms.getValue(Currency.USD).digits)

        vm.removeOpening(Currency.BRL)
        val refused = vm.state.awaitUntil { it.openingForms.getValue(Currency.BRL).error == OpeningError.HAS_ADJUSTMENTS }
        assertNotNull(refused.openings[Currency.BRL])
    }

    @Test
    fun openingScreen_saveNeedsAnAmount_andASavedOneCanBeRemoved() {
        val today = d("2026-11-12")
        val state = BalanceUiState(
            loading = false,
            today = today,
            openings = mapOf(Currency.BRL to opening(brl("10000.00"), "2026-11-01"), Currency.USD to null),
            openingForms = mapOf(
                Currency.BRL to OpeningForm(digits = "1000000", date = d("2026-11-01")),
                Currency.USD to OpeningForm(date = today),
            ),
        )
        compose.setContent { FinanceTheme { OpeningBalancesContent(state, {}, { _, _ -> }, {}, {}) } }
        compose.onAllNodesWithText("Save")[0].assertIsEnabled()
        compose.onAllNodesWithText("Save")[1].assertIsNotEnabled()
        compose.onAllNodesWithText("Remove").assertCountEquals(1)
    }

    @Test
    fun screenshots_opening() {
        runBlocking { cash.setOpening(brl("10000.00"), d("2026-11-01")) }
        val vm = BalanceViewModel(cash, time)
        val state = runBlocking { vm.state.awaitUntil { it.openingForms.getValue(Currency.BRL).digits.isNotEmpty() } }
        compose.snapBothThemes("opening_balances") { OpeningBalancesContent(state, {}, { _, _ -> }, {}, {}) }
    }

    @Test
    fun adjustment_isRealMinusComputed_andNegativeAllowed() = runBlocking {
        cash.setOpening(brl("1000.00"), d("2026-11-01"))
        val vm = BalanceViewModel(cash, time)
        vm.state.awaitUntil { it.openings[Currency.BRL] != null }
        vm.editAdjust { it.copy(digits = "25000", negative = true) }
        assertEquals(brl("-1250.00"), vm.state.awaitUntil { it.adjustDifference != null }.adjustDifference)
        vm.saveAdjustment()
        vm.state.awaitUntil { it.adjustSaved }
        assertEquals(brl("-250.00"), CashCalculator.balance(Currency.BRL, d("2026-11-12"), cash.snapshot()))
    }

    @Test
    fun adjustment_withoutOpening_asksForIt() = runBlocking {
        val vm = BalanceViewModel(cash, time)
        vm.state.awaitUntil { !it.loading }
        vm.editAdjust { it.copy(digits = "100") }
        vm.saveAdjustment()
        assertEquals(BalanceMessage.NEEDS_OPENING, vm.state.awaitUntil { it.message != null }.message)
    }

    @Test
    fun screenshots() {
        runBlocking {
            cash.setOpening(brl("10000.00"), d("2026-11-01"))
            cash.addAdjustment(brl("9800.00"), d("2026-11-10"), "bank statement")
        }
        val vm = BalanceViewModel(cash, time)
        val state = runBlocking { vm.state.awaitUntil { it.adjustments.isNotEmpty() } }
        compose.snapBothThemes("adjust_balance") { AdjustBalanceContent(state.copy(adjust = state.adjust.copy(digits = "975000")), AdjustActions()) }
    }
}
