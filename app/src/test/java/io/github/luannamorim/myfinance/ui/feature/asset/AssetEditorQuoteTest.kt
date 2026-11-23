package io.github.luannamorim.myfinance.ui.feature.asset

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.testing.invoke
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.core.format.NBSP
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.data.images.ImageStore
import io.github.luannamorim.myfinance.data.repository.BackupTrigger
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.data.repository.PortfolioRepository
import io.github.luannamorim.myfinance.domain.market.TickerMatch
import io.github.luannamorim.myfinance.domain.market.TickerType
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.testutil.FakeQuoteClient
import io.github.luannamorim.myfinance.testutil.MainDispatcherRule
import io.github.luannamorim.myfinance.testutil.PHONE_TALL
import io.github.luannamorim.myfinance.testutil.awaitUntil
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.inMemoryDb
import io.github.luannamorim.myfinance.testutil.snapBothThemes
import io.github.luannamorim.myfinance.testutil.testPrices
import io.github.luannamorim.myfinance.testutil.testRates
import io.github.luannamorim.myfinance.testutil.testSettings
import io.github.luannamorim.myfinance.testutil.usd
import io.github.luannamorim.myfinance.ui.navigation.AssetEditorRoute
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = PHONE_TALL)
class AssetEditorQuoteTest {

    @get:Rule val main = MainDispatcherRule()
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = inMemoryDb(seed = true)
    private val time = FakeTimeSource()
    private val images = ImageStore(context, Dispatchers.Unconfined)
    private val portfolio = PortfolioRepository(db, time, BackupTrigger { }, images)
    private val categories = CategoryRepository(db, time, BackupTrigger { })
    private val settings = testSettings()
    private val rates = testRates(db, settings, time)
    private val client = FakeQuoteClient(
        chart = { symbol, _ -> resource(if (symbol == "BTC-USD") "btc.json" else "nvda_three_bars.json") },
        search = { q -> resource(if (q.startsWith("bit")) "search_bitcoin.json" else "search_nvd.json") },
    )
    private val prices = testPrices(db, settings, time, client)
    private val appScope = TestScope()

    private val nvda = TickerMatch("NVDA", "NVIDIA Corporation", TickerType.STOCK, "NASDAQ")
    private val btc = TickerMatch("BTC-USD", "Bitcoin USD", TickerType.CRYPTO, "CCC")

    private fun resource(name: String) = javaClass.getResource("/yahoo/$name")!!.readText()

    private fun category(key: String) = runBlocking { db.categoryDao().getAllAssetCategories().first { it.seedKey == key }.id }

    private fun editor(scope: CoroutineScope = appScope) =
        AssetEditorViewModel(SavedStateHandle(route = AssetEditorRoute()), portfolio, categories, settings, images, rates, time, prices, scope)

    /** Runs the debounced search (300 ms on the main test dispatcher). */
    private fun advanceDebounce() {
        main.dispatcher.scheduler.advanceTimeBy(300)
        main.dispatcher.scheduler.runCurrent()
    }

    @Test
    fun createCrypto_viaUi() {
        time.set("2026-11-23") // btc.json's last bar; later bars would be dropped as future ones
        val vm = editor()
        compose.setContent { FinanceTheme { AssetEditorScreen(onClose = {}, viewModel = vm) } }
        compose.waitUntil(5_000) { vm.state.value.categories.isNotEmpty() && !vm.state.value.loading }
        compose.onNodeWithText("From quote").performClick()
        compose.onNodeWithText("Ticker").performTextInput("bitcoin")
        advanceDebounce()
        compose.waitUntil(5_000) { vm.state.value.matches.isNotEmpty() }
        compose.onNodeWithText("BTC-USD").performClick()
        compose.waitUntil(5_000) { vm.state.value.categoryId != null && vm.state.value.preview != null }
        val picked = vm.state.value
        assertEquals("BTC", picked.name)
        assertEquals("Bitcoin", picked.description)
        assertEquals(category("asset.cryptocurrencies"), picked.categoryId)
        assertEquals(Currency.USD, picked.currency)
        compose.onNodeWithText("Price per BTC").assertExists()
        compose.onNodeWithText("Quantity").performTextInput("0,05")
        compose.onNodeWithText("Price per BTC").performTextInput("63.336,84")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { vm.state.value.done }
        val data = runBlocking { portfolio.data.first() }
        val asset = data.assets.single()
        assertEquals("BTC-USD", asset.ticker)
        val initial = data.flowsOf(asset).single()
        assertEquals(FlowKind.INITIAL, initial.kind)
        assertEquals(usd("3166.84"), initial.amount)
        assertEquals(BigDecimal("0.05"), initial.quantityDelta)
    }

    @Test
    fun preview_beforeSplit_showsNoteAndTodayQuantity() {
        val vm = editor()
        compose.setContent { FinanceTheme { AssetEditorScreen(onClose = {}, viewModel = vm) } }
        compose.waitUntil(5_000) { !vm.state.value.loading }
        vm.edit { it.copy(tracked = true, acquisitionDate = d("2024-06-03"), quantity = "10", priceText = "1.150,00") }
        vm.pickTicker(nvda)
        compose.waitUntil(5_000) { vm.state.value.preview != null }
        val s = vm.state.value
        assertEquals(d("2024-05-27"), client.chartRequests.last().second) // a week before the purchase
        assertEquals(1, s.preview!!.splitsSince.size)
        assertEquals(0, BigDecimal(100).compareTo(s.todayQuantity))
        assertEquals(usd("12179.00"), s.todayValue)
        assertEquals(usd("11500.00"), s.cost)
        compose.onNodeWithText("Includes a 10:1 split on 10/06/2024", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Today: 100 shares × US\$${NBSP}121,79 = US\$${NBSP}12.179,00").assertExists()

        // A purchase after the split: no note, the quantity stays.
        vm.edit { it.copy(acquisitionDate = d("2024-06-10")) }
        compose.waitUntil(5_000) { vm.state.value.preview?.splitsSince?.isEmpty() == true }
        assertEquals(usd("1217.90"), vm.state.value.todayValue)
        compose.onNodeWithText("Includes a 10:1 split", substring = true).assertDoesNotExist()
    }

    @Test
    fun search_offline_showsErrorAndBlocksSave() = runBlocking {
        client.search = { throw IOException("offline") }
        val vm = editor()
        vm.state.awaitUntil { !it.loading }
        vm.edit { it.copy(tracked = true) }
        vm.onQuery("nvd")
        advanceDebounce()
        val s = vm.state.awaitUntil { it.searchError }
        assertTrue(s.matches.isEmpty())
        assertFalse(s.searching)
        vm.edit { it.copy(name = "NVDA", categoryId = category("asset.stocks"), quantity = "10", priceText = "121,28") }
        assertFalse(vm.save())
        assertTrue(vm.state.awaitUntil { it.tickerError }.tickerError)
        assertTrue(portfolio.data.first().assets.isEmpty())
    }

    @Test
    fun search_needsDebounceAndText() = runBlocking {
        val vm = editor()
        vm.state.awaitUntil { !it.loading }
        vm.onQuery("n")
        vm.onQuery("nv")
        vm.onQuery("nvd")
        main.dispatcher.scheduler.advanceTimeBy(299)
        main.dispatcher.scheduler.runCurrent()
        assertTrue(client.searchRequests.isEmpty())
        advanceDebounce()
        assertEquals(listOf("nvd"), client.searchRequests)
        assertEquals("NVDA", vm.state.awaitUntil { it.matches.isNotEmpty() }.matches[1].symbol)
        vm.onQuery("")
        advanceDebounce()
        assertEquals(1, client.searchRequests.size)
        assertTrue(vm.state.awaitUntil { it.matches.isEmpty() }.matches.isEmpty())
    }

    @Test
    fun save_tracked_requiresQuantityAndPrice() = runBlocking {
        val vm = editor()
        vm.state.awaitUntil { !it.loading }
        vm.edit { it.copy(tracked = true) }
        vm.pickTicker(nvda)
        vm.state.awaitUntil { it.categoryId != null }
        vm.edit { it.copy(quantity = "1.5", priceText = "") }
        assertFalse(vm.save())
        val s = vm.state.awaitUntil { it.quantityError }
        assertTrue(s.priceError)
        assertFalse(s.tickerError)
    }

    @Test
    fun repick_replacesAutoFilledFields() = runBlocking {
        val vm = editor()
        vm.state.awaitUntil { !it.loading }
        vm.edit { it.copy(tracked = true) }
        vm.pickTicker(nvda)
        vm.state.awaitUntil { it.categoryId == category("asset.stocks") }
        vm.clearTicker()
        val cleared = vm.state.awaitUntil { it.ticker == null }
        assertEquals("", cleared.name)
        assertEquals("", cleared.description)
        assertEquals(null, cleared.categoryId)
        vm.pickTicker(btc)
        val s = vm.state.awaitUntil { it.categoryId == category("asset.cryptocurrencies") }
        assertEquals("BTC", s.name)
        assertEquals("Bitcoin", s.description)
    }

    @Test
    fun repick_keepsUserEditedFields() = runBlocking {
        time.set("2026-11-23") // btc.json's last bar; later bars would be dropped as future ones
        val vm = editor()
        vm.state.awaitUntil { !it.loading }
        vm.edit { it.copy(tracked = true) }
        vm.pickTicker(nvda)
        vm.state.awaitUntil { it.categoryId == category("asset.stocks") }
        vm.edit { it.copy(name = "My NVIDIA", categoryId = category("asset.other_investments")) }
        vm.clearTicker()
        vm.pickTicker(btc)
        vm.state.awaitUntil { it.preview != null }
        Thread.sleep(200) // the category lookup would have landed by now
        val s = vm.state.value
        assertEquals("My NVIDIA", s.name)
        assertEquals("Bitcoin", s.description)
        assertEquals(category("asset.other_investments"), s.categoryId)
    }

    @Test
    fun switchToManual_dropsTickerAndQuantity() = runBlocking {
        val vm = editor()
        vm.state.awaitUntil { !it.loading }
        vm.edit { it.copy(tracked = true, quantity = "1.000") }
        vm.onQuery("nvd")
        advanceDebounce()
        vm.pickTicker(nvda)
        vm.state.awaitUntil { it.categoryId != null && it.preview != null }
        vm.edit { it.copy(tracked = false) }
        val s = vm.state.awaitUntil { !it.tracked }
        assertEquals("", s.quantity)
        assertEquals(settings.current().defaultCurrency, s.currency)
        assertEquals(null, s.ticker)
        assertEquals(null, s.preview)
        assertFalse(s.previewError)
        assertEquals("", s.query)
        assertTrue(s.matches.isEmpty())
        val chartCalls = client.chartRequests.size
        vm.edit { it.copy(acquisitionDate = d("2024-06-03")) }
        assertEquals(chartCalls, client.chartRequests.size)

        vm.edit { it.copy(name = "Tesouro", categoryId = category("asset.fixed_income"), investedDigits = "100000") }
        assertTrue(vm.save())
        vm.state.awaitUntil { it.done }
        val asset = portfolio.data.first().assets.single()
        assertEquals(null, asset.ticker)
        assertEquals(settings.current().defaultCurrency, asset.currency)
        assertEquals(null, asset.quantity)
    }

    @Test
    fun saveTracked_refreshesOnAppScope() = runBlocking {
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { editor() } })[AssetEditorViewModel::class]
        vm.state.awaitUntil { !it.loading }
        vm.edit { it.copy(tracked = true, quantity = "10", priceText = "121,28") }
        vm.pickTicker(nvda)
        vm.state.awaitUntil { it.categoryId != null && it.preview != null }
        client.chartRequests.clear()
        assertTrue(vm.save())
        vm.state.awaitUntil { it.done }
        store.clear()
        assertTrue(client.chartRequests.isEmpty())
        val deadline = System.currentTimeMillis() + 5_000
        while (client.chartRequests.none { it.first == "NVDA" } && System.currentTimeMillis() < deadline) {
            appScope.advanceUntilIdle()
            Thread.sleep(10)
        }
        assertEquals("NVDA", client.chartRequests.single().first)
    }

    @Test
    fun screenshot_quoteEditor() {
        val vm = editor()
        val state = runBlocking {
            vm.state.awaitUntil { !it.loading && it.categories.isNotEmpty() }
            vm.edit { it.copy(tracked = true, acquisitionDate = d("2024-06-03"), quantity = "10", priceText = "1.150,00", institution = "Avenue") }
            vm.pickTicker(nvda)
            vm.state.awaitUntil { it.categoryId != null && it.preview != null }
        }
        compose.snapBothThemes("asset_editor_quote") { AssetEditorContent(state, AssetEditorActions()) }
    }
}
