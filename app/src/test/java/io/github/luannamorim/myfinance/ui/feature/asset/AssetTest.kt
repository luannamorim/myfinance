package io.github.luannamorim.myfinance.ui.feature.asset

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.data.images.ImageStore
import io.github.luannamorim.myfinance.data.prefs.RateStateStore
import io.github.luannamorim.myfinance.data.rates.RateClient
import io.github.luannamorim.myfinance.data.repository.AssetDraft
import io.github.luannamorim.myfinance.data.repository.BackupTrigger
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.data.repository.PortfolioRepository
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.testutil.MainDispatcherRule
import io.github.luannamorim.myfinance.testutil.PHONE_TALL
import io.github.luannamorim.myfinance.testutil.awaitUntil
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.inMemoryDb
import io.github.luannamorim.myfinance.testutil.snapBothThemes
import io.github.luannamorim.myfinance.testutil.testSettings
import io.github.luannamorim.myfinance.testutil.usd
import io.github.luannamorim.myfinance.ui.navigation.AssetDetailRoute
import io.github.luannamorim.myfinance.ui.navigation.AssetEditorRoute
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = PHONE_TALL)
class AssetTest {

    @get:Rule val main = MainDispatcherRule()
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = inMemoryDb(seed = true)
    private val time = FakeTimeSource()
    private val images = ImageStore(context, Dispatchers.Unconfined)
    private val portfolio = PortfolioRepository(db, time, BackupTrigger { }, images)
    private val categories = CategoryRepository(db, time, BackupTrigger { })
    private val settings = testSettings()
    private val rates = ExchangeRateRepository(
        db,
        RateClient { _, _ -> "[]" },
        RateStateStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { File(Files.createTempDirectory("r").toFile(), "r.preferences_pb") }),
        settings,
        time,
        BackupTrigger { },
    )

    // The in-memory database is not closed: ViewModel collectors may still be querying it when the test ends.

    private fun category(key: String) = runBlocking { db.categoryDao().getAllAssetCategories().first { it.seedKey == key }.id }

    private fun editor(route: AssetEditorRoute = AssetEditorRoute()) =
        AssetEditorViewModel(SavedStateHandle(route = route), portfolio, categories, settings, images, time)

    private fun detail(id: Long) = AssetDetailViewModel(SavedStateHandle(route = AssetDetailRoute(id)), portfolio, rates, settings, images, time)

    @Test
    fun createAsset_throughTheUi() {
        val vm = editor()
        compose.setContent { FinanceTheme { AssetEditorScreen(onClose = {}, viewModel = vm) } }
        compose.waitUntil(5_000) { vm.state.value.categories.isNotEmpty() && !vm.state.value.loading }
        compose.onNodeWithText("Name").performTextInput("Tesouro Selic")
        compose.onNodeWithText("Fixed Income").performClick()
        compose.onNodeWithText("Initial invested amount").performTextInput("500000")
        compose.onNodeWithText("Current estimated value (optional)").performTextInput("512000")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { vm.state.value.done }
        val data = runBlocking { portfolio.data.first() }
        val asset = data.assets.single()
        assertEquals("Tesouro Selic", asset.name)
        assertEquals(brl("5000.00"), data.flowsOf(asset).single().amount)
        assertEquals(brl("5120.00"), data.valuationsOf(asset).single().value)
    }

    @Test
    fun quantity_isValidated() = runBlocking {
        val vm = editor()
        vm.state.awaitUntil { !it.loading }
        vm.edit { it.copy(name = "BTC", categoryId = category("asset.cryptocurrencies"), quantity = "0,00012345") }
        vm.save()
        vm.state.awaitUntil { it.done }
        assertEquals(BigDecimal("0.00012345"), portfolio.data.first().assets.single().quantity)

        val bad = editor()
        bad.state.awaitUntil { !it.loading }
        bad.edit { it.copy(name = "X", categoryId = category("asset.stocks"), quantity = "1,2,3") }
        bad.save()
        assertEquals(true, bad.state.awaitUntil { it.quantityError }.quantityError)
    }

    @Test
    fun updateValuation_throughTheUi() {
        val id = runBlocking { portfolio.createAsset(AssetDraft("Tesouro Selic", category("asset.fixed_income"), Currency.BRL, acquisitionDate = d("2026-01-10")), brl("5000.00"), null) }
        val vm = detail(id)
        compose.setContent { FinanceTheme { AssetDetailScreen(onBack = {}, onEdit = {}, viewModel = vm) } }
        compose.waitUntil(5_000) { vm.state.value.snapshot != null }
        compose.onNodeWithText("Update value").performClick()
        compose.onNode(hasText("Estimated value") and hasSetTextAction()).performTextInput("512000")
        compose.onAllNodesWithText("Save").onLast().performClick()
        compose.waitUntil(5_000) { vm.state.value.valuations.isNotEmpty() }
        val s = vm.state.value.snapshot!!
        assertEquals(brl("5120.00"), s.value)
        assertEquals(brl("120.00"), s.gain)
        assertEquals(BigDecimal("0.0240000000"), s.simpleReturn)
    }

    @Test
    fun contributeAndWithdraw_areSeparateFromPerformance() = runBlocking {
        val id = portfolio.createAsset(AssetDraft("Tesouro", category("asset.fixed_income"), Currency.BRL, acquisitionDate = d("2026-01-10")), brl("5000.00"), null)
        portfolio.addValuation(id, d("2026-03-31"), brl("5120.00"))
        val vm = detail(id)
        vm.state.awaitUntil { it.snapshot != null }
        vm.addFlow(FlowKind.CONTRIBUTION, d("2026-04-15"), brl("1000.00"), null, null, null)
        val s = vm.state.awaitUntil { it.flows.size == 2 }.snapshot!!
        assertEquals(brl("6120.00"), s.value)
        assertEquals(brl("120.00"), s.gain)
    }

    @Test
    fun usdAsset_showsConvertedFigures() = runBlocking {
        val id = portfolio.createAsset(AssetDraft("VOO", category("asset.etfs"), Currency.USD, acquisitionDate = d("2026-02-02")), usd("1000.00"), usd("1100.00"))
        rates.setManual(d("2026-02-02"), BigDecimal("5.0000"))
        rates.setManual(d("2026-11-12"), BigDecimal("5.6143"))
        val state = detail(id).state.awaitUntil { it.converted != null && !it.converted!!.value.incomplete }
        assertEquals(brl("1175.73"), state.converted!!.gain.money)
        assertNotNull(state.rate)
    }

    @Test
    fun screenshots() {
        val id = runBlocking {
            val id = portfolio.createAsset(AssetDraft("Tesouro Selic 2029", category("asset.fixed_income"), Currency.BRL, institution = "Tesouro Direto", acquisitionDate = d("2026-01-10")), brl("5000.00"), null)
            portfolio.addValuation(id, d("2026-03-31"), brl("5120.00"))
            portfolio.addFlow(id, FlowKind.CONTRIBUTION, d("2026-04-15"), brl("1000.00"))
            portfolio.addValuation(id, d("2026-09-30"), brl("6410.00"))
            id
        }
        val state = runBlocking { detail(id).state.awaitUntil { it.valuations.size == 2 } }
        compose.snapBothThemes("asset_detail") { AssetDetailContent(state, AssetDetailActions()) }
    }

    @Test
    fun screenshot_editor() {
        val state = runBlocking { editor().state.awaitUntil { it.categories.isNotEmpty() && !it.loading } }
        compose.snapBothThemes("asset_editor") {
            AssetEditorContent(state.copy(name = "VOO", categoryId = category("asset.etfs"), currency = Currency.USD, investedDigits = "100000", institution = "Avenue"), AssetEditorActions())
        }
    }
}
