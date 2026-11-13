package io.github.luannamorim.myfinance.ui.feature.overview

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.data.images.ImageStore
import io.github.luannamorim.myfinance.data.prefs.RateStateStore
import io.github.luannamorim.myfinance.data.rates.RateClient
import io.github.luannamorim.myfinance.data.repository.BackupTrigger
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.data.repository.PortfolioRepository
import io.github.luannamorim.myfinance.data.repository.TransactionDraft
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
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.domain.model.Valuation
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioData
import io.github.luannamorim.myfinance.testutil.MainDispatcherRule
import io.github.luannamorim.myfinance.testutil.PHONE_TALL
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.confirmed
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.inMemoryDb
import io.github.luannamorim.myfinance.testutil.opening
import io.github.luannamorim.myfinance.testutil.pending
import io.github.luannamorim.myfinance.testutil.rate
import io.github.luannamorim.myfinance.testutil.snapBothThemes
import io.github.luannamorim.myfinance.testutil.testSettings
import io.github.luannamorim.myfinance.testutil.usd
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = PHONE_TALL)
class OverviewTest {

    @get:Rule val main = MainDispatcherRule()
    @get:Rule val compose = createComposeRule()

    private val today = d("2026-11-12")
    private val categories = listOf(
        Category(10, null, EntryKind.INCOME, "work", "income.salary", false, 0),
        Category(20, null, EntryKind.EXPENSE, "key", "expense.rent", false, 1),
        Category(30, null, EntryKind.EXPENSE, "bolt", "expense.electricity", false, 2),
        Category(40, null, EntryKind.INCOME, "laptop", "income.freelance", false, 3),
        Category(50, null, EntryKind.EXPENSE, "shopping_basket", "expense.groceries", false, 4),
    )
    private val cash = CashLedger(
        adjustments = listOf(opening(brl("10000.00"), "2026-11-01"), opening(usd("500.00"), "2026-11-01")),
        occurrences = listOf(
            confirmed(EntryKind.INCOME, brl("9000.00"), "2026-11-05", category = 10, name = "Salary"),
            confirmed(EntryKind.EXPENSE, brl("2500.00"), "2026-11-10", actual = brl("2550.00"), category = 20, name = "Rent"),
            confirmed(EntryKind.EXPENSE, brl("312.40"), "2026-11-12", category = 50, name = "Groceries"),
            pending(EntryKind.EXPENSE, brl("250.00"), "2026-11-20", category = 30, name = "Electricity"),
            pending(EntryKind.INCOME, usd("1000.00"), "2026-11-25", category = 40, name = "Website"),
            pending(EntryKind.EXPENSE, brl("2500.00"), "2026-12-10", category = 20, name = "Rent"),
        ),
    )
    private val assetCategories = listOf(
        AssetCategory(1, null, AssetGroup.INVESTMENT, "receipt_long", "asset.fixed_income", false, 0),
        AssetCategory(3, null, AssetGroup.PERSONAL, "directions_car", "asset.vehicles", false, 0),
    )
    private val tesouro = Asset(10, "Tesouro Selic", 1, Currency.BRL, null, null, d("2026-01-10"), null, null, null)
    private val car = Asset(12, "Car", 3, Currency.BRL, null, null, d("2026-01-10"), null, null, null)
    private val portfolio = PortfolioData(
        assets = listOf(tesouro, car),
        flows = mapOf(
            tesouro.id to listOf(AssetFlow(1, tesouro.id, FlowKind.INITIAL, d("2026-01-10"), brl("85000.00"), null, null)),
            car.id to listOf(AssetFlow(2, car.id, FlowKind.INITIAL, d("2026-01-10"), brl("45000.00"), null, null)),
        ),
        valuations = mapOf(
            tesouro.id to listOf(Valuation(3, tesouro.id, d("2026-10-31"), brl("91240.00"), null)),
            car.id to listOf(Valuation(4, car.id, d("2026-10-01"), brl("40000.00"), null)),
        ),
        categories = assetCategories,
    )
    private val rates = ExchangeRates(listOf(rate("2026-11-12", "5.6143")))

    @Test
    fun screenshots() {
        compose.snapBothThemes("overview") { OverviewContent(overviewState(cash, portfolio, categories, rates, Currency.BRL, today), OverviewActions()) }
    }

    @Test
    fun screenshot_empty_showsOnboarding() {
        compose.snapBothThemes("overview_empty") { OverviewContent(overviewState(CashLedger(), PortfolioData(), categories, rates, Currency.BRL, today), OverviewActions()) }
        compose.onNodeWithText("Set your opening balance").assertIsDisplayed()
    }

    @Test
    fun confirmFromUpcoming_throughTheUi() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = inMemoryDb(seed = true)
        try {
            val time = FakeTimeSource()
            val settings = testSettings()
            val cashRepo = CashRepository(db, time, BackupTrigger { })
            val rateRepo = ExchangeRateRepository(
                db, RateClient { _, _ -> "[]" },
                RateStateStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { File(Files.createTempDirectory("r").toFile(), "r.preferences_pb") }),
                settings, time, BackupTrigger { },
            )
            val id = runBlocking {
                val electricity = db.categoryDao().getAll().first { it.seedKey == "expense.electricity" }.id
                cashRepo.create(TransactionDraft("Electricity", electricity, EntryKind.EXPENSE, brl("250.00"), d("2026-11-12"), null))
            }
            val vm = OverviewViewModel(
                cashRepo,
                PortfolioRepository(db, time, BackupTrigger { }, ImageStore(context, Dispatchers.Unconfined)),
                CategoryRepository(db, time, BackupTrigger { }),
                rateRepo,
                settings,
                time,
            )
            compose.setContent { FinanceTheme { OverviewScreen(OverviewActions(), viewModel = vm) } }
            compose.waitUntil(5_000) { vm.state.value.upcoming.isNotEmpty() }
            compose.onAllNodesWithText("Confirm").onFirst().performClick()
            compose.onNodeWithText("Confirm payment").assertIsDisplayed()
            // The sheet's button (the row also has a "Confirm" action).
            compose.onAllNodesWithText("Confirm").onLast().performClick()
            compose.waitUntil(5_000) { vm.state.value.upcoming.isEmpty() }
            assertEquals(OccurrenceState.CONFIRMED, runBlocking { cashRepo.occurrence(id)!!.state })
        } finally {
            // Not closed: the ViewModel's collectors may still query it.
        }
    }
}
