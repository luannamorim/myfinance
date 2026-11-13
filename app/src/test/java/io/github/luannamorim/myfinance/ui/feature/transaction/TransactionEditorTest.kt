package io.github.luannamorim.myfinance.ui.feature.transaction

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.data.images.ImageStore
import io.github.luannamorim.myfinance.data.repository.AssetDraft
import io.github.luannamorim.myfinance.data.repository.BackupTrigger
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.data.repository.PortfolioRepository
import io.github.luannamorim.myfinance.data.repository.TransactionDraft
import io.github.luannamorim.myfinance.domain.cash.RecurrenceRule
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.testutil.MainDispatcherRule
import io.github.luannamorim.myfinance.testutil.PHONE_TALL
import io.github.luannamorim.myfinance.testutil.awaitUntil
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.inMemoryDb
import io.github.luannamorim.myfinance.testutil.snapBothThemes
import io.github.luannamorim.myfinance.testutil.testRates
import io.github.luannamorim.myfinance.testutil.testSettings
import io.github.luannamorim.myfinance.ui.common.TransferTarget
import io.github.luannamorim.myfinance.ui.navigation.TransactionEditorRoute
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal

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
    private val portfolio = PortfolioRepository(db, time, BackupTrigger { }, ImageStore(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined))
    private val rates = testRates(db, settings, time)

    // The in-memory database is not closed: ViewModel collectors may still be querying it when the test ends.

    private fun editor(route: TransactionEditorRoute = TransactionEditorRoute()) =
        TransactionEditorViewModel(SavedStateHandle(route = route), cash, categories, portfolio, rates, settings, time)

    private fun categoryId(seedKey: String) = runBlocking { db.categoryDao().getAll().first { it.seedKey == seedKey }.id }

    @Test
    fun missingFields_flagErrors_andSaveNothing() = runBlocking {
        val vm = editor()
        vm.state.awaitUntil { !it.loading }
        assertFalse(vm.save())
        val s = vm.state.awaitUntil { it.nameError }
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
        assertTrue(vm.state.awaitUntil { it.categoryId != null }.canSettle)
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
        assertFalse(vm.state.awaitUntil { it.repeat != RepeatChoice.Never }.canSettle)
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

    private fun asset(name: String, currency: Currency, seedKey: String = "asset.fixed_income"): Long = runBlocking {
        val category = db.categoryDao().getAllAssetCategories().first { it.seedKey == seedKey }.id
        val initial = Money(100000, currency)
        portfolio.createAsset(AssetDraft(name, category, currency, acquisitionDate = d("2026-01-10")), initial, null)
    }

    @Test
    fun transfer_toAnAsset_isSavedDone_withItsContribution() = runBlocking {
        val tesouro = asset("Tesouro Selic", Currency.BRL)
        val vm = editor()
        vm.state.awaitUntil { !it.loading }
        vm.setKind(EntryKind.TRANSFER_OUT)
        vm.state.awaitUntil { it.assets.isNotEmpty() }
        vm.setAsset(tesouro)
        vm.setDigits("100000")
        // No name, no category: the asset names it.
        assertTrue(vm.save())
        vm.state.awaitUntil { it.done }
        val row = cash.snapshot().occurrences.single()
        assertEquals(EntryKind.TRANSFER_OUT, row.kind)
        assertEquals(OccurrenceState.CONFIRMED, row.state)
        assertEquals("Tesouro Selic", row.name)
        assertEquals(row.id, db.portfolioDao().getFlows(tesouro).single { it.kind == FlowKind.CONTRIBUTION }.occurrenceId)
    }

    @Test
    fun transfer_withoutAsset_flagsIt() = runBlocking {
        asset("Tesouro Selic", Currency.BRL)
        val vm = editor()
        vm.state.awaitUntil { !it.loading }
        vm.setKind(EntryKind.TRANSFER_OUT)
        vm.setDigits("100000")
        assertFalse(vm.save())
        assertTrue(vm.state.awaitUntil { it.assetError }.assetError)
        assertTrue(cash.snapshot().occurrences.isEmpty())
    }

    @Test
    fun transfer_betweenCurrencies_suggestsWhatTheAssetReceived() = runBlocking {
        val voo = asset("VOO", Currency.USD, "asset.etfs")
        rates.setManual(d("2026-11-12"), BigDecimal("5.0000"))
        val vm = editor()
        vm.state.awaitUntil { !it.loading }
        vm.setKind(EntryKind.TRANSFER_OUT)
        vm.state.awaitUntil { it.assets.isNotEmpty() && it.rates.latest() != null }
        vm.setCurrency(Currency.BRL)
        vm.setAsset(voo)
        vm.setDigits("50000")
        // R$ 500,00 at 5,0000 = US$ 100,00, until typed over.
        assertEquals("10000", vm.state.awaitUntil { it.needsAssetAmount }.assetAmountDigits)
        assertTrue(vm.save())
        vm.state.awaitUntil { it.done }
        assertEquals(10000L, db.portfolioDao().getFlows(voo).single { it.kind == FlowKind.CONTRIBUTION }.amount)
    }

    @Test
    fun screenshot_transfer() = compose.snapBothThemes("transfer_editor") {
        TransactionEditorContent(
            TransactionEditorUiState(
                loading = false, kind = EntryKind.TRANSFER_OUT, digits = "100000", date = d("2026-11-12"), today = d("2026-11-12"),
                repeat = RepeatChoice.Preset(RecurrenceRule.Preset.MONTHLY), assetId = 1,
                assets = listOf(
                    TransferTarget(1, "Tesouro Selic 2029", Currency.BRL, d("2026-01-10"), closed = false),
                    TransferTarget(2, "VOO", Currency.USD, d("2026-02-02"), closed = false),
                    TransferTarget(3, "Loan to Ana", Currency.BRL, d("2026-05-01"), closed = false),
                ),
            ),
            TransactionEditorActions(),
        )
    }
}

