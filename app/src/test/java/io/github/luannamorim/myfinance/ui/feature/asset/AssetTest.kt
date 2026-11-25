package io.github.luannamorim.myfinance.ui.feature.asset

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.luannamorim.myfinance.core.format.Formats
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.data.db.entity.AssetPriceEntity
import io.github.luannamorim.myfinance.data.images.ImageKind
import io.github.luannamorim.myfinance.data.images.ImageStore
import io.github.luannamorim.myfinance.data.prefs.RateStateStore
import io.github.luannamorim.myfinance.data.rates.RateClient
import io.github.luannamorim.myfinance.data.repository.AssetDraft
import io.github.luannamorim.myfinance.data.repository.BackupTrigger
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.data.repository.PortfolioRepository
import io.github.luannamorim.myfinance.domain.model.AppSettings
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.NumberFormatStyle
import io.github.luannamorim.myfinance.domain.model.PriceSource
import io.github.luannamorim.myfinance.domain.model.ThemeMode
import io.github.luannamorim.myfinance.testutil.FakeQuoteClient
import io.github.luannamorim.myfinance.testutil.MainDispatcherRule
import io.github.luannamorim.myfinance.testutil.PHONE_TALL
import io.github.luannamorim.myfinance.testutil.SCREENSHOT_DIR
import io.github.luannamorim.myfinance.testutil.awaitUntil
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.inMemoryDb
import io.github.luannamorim.myfinance.testutil.snapBothThemes
import io.github.luannamorim.myfinance.testutil.testPrices
import io.github.luannamorim.myfinance.testutil.testSettings
import io.github.luannamorim.myfinance.testutil.usd
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.navigation.AssetDetailRoute
import io.github.luannamorim.myfinance.ui.navigation.AssetEditorRoute
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.util.Locale

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

    private val quotes = FakeQuoteClient(
        chart = { _, _ -> javaClass.getResource("/yahoo/nvda_three_bars.json")!!.readText() },
        search = { javaClass.getResource("/yahoo/search_nvd.json")!!.readText() },
    )
    private val prices = testPrices(db, settings, time, quotes)
    private val appScope = TestScope()

    private fun editor(route: AssetEditorRoute = AssetEditorRoute()) =
        AssetEditorViewModel(SavedStateHandle(route = route), portfolio, categories, settings, images, rates, time, prices, appScope)

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
    fun createTracked_viaUi() {
        val vm = editor()
        compose.setContent { FinanceTheme { AssetEditorScreen(onClose = {}, viewModel = vm) } }
        compose.waitUntil(5_000) { vm.state.value.categories.isNotEmpty() && !vm.state.value.loading }
        compose.onNodeWithText("From quote").performClick()
        compose.onNodeWithText("Ticker").performTextInput("nvd")
        main.dispatcher.scheduler.advanceTimeBy(300)
        main.dispatcher.scheduler.runCurrent()
        compose.waitUntil(5_000) { vm.state.value.matches.isNotEmpty() }
        compose.onNodeWithText("NVDA").performClick()
        compose.waitUntil(5_000) { vm.state.value.categoryId != null }
        compose.onNodeWithText("Quantity").performTextInput("10")
        compose.onNodeWithText("Price per share").performTextInput("121,28")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { vm.state.value.done }
        val data = runBlocking { portfolio.data.first() }
        val asset = data.assets.single()
        assertEquals("NVDA", asset.ticker)
        assertEquals(Currency.USD, asset.currency)
        assertEquals(category("asset.stocks"), asset.categoryId)
        val initial = data.flowsOf(asset).single()
        assertEquals(FlowKind.INITIAL, initial.kind)
        assertEquals(usd("1212.80"), initial.amount)
        assertEquals(BigDecimal("10"), initial.quantityDelta)
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
        vm.addFlow(FlowKind.CONTRIBUTION, d("2026-04-15"), brl("1000.00"), null, null, null, null)
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
    }

    private fun fromCash(name: String = "CDB") = runBlocking {
        portfolio.createAsset(AssetDraft(name, category("asset.fixed_income"), Currency.BRL, acquisitionDate = d("2026-11-05")), brl("3000.00"), null, fromCash = brl("3000.00"))
    }

    private fun openDeleteDialog(vm: AssetDetailViewModel) {
        compose.setContent { FinanceTheme { DotFormat { AssetDetailScreen(onBack = {}, onEdit = {}, viewModel = vm) } } }
        compose.waitUntil(5_000) { vm.state.value.snapshot != null && vm.state.value.cashMoved.isNotEmpty() }
        compose.onNodeWithContentDescription("Delete").performClick()
    }

    @Test
    fun deleteAsset_undoesItsTransfers_byDefault() {
        val vm = detail(fromCash())
        openDeleteDialog(vm)
        compose.onNodeWithText("R$ 3,000.00 back to cash").assertExists()
        compose.onAllNodesWithText("Delete").onLast().performClick()
        compose.waitUntil(5_000) { vm.state.value.gone }
        assertTrue(runBlocking { db.cashDao().getAllOccurrences() }.isEmpty())
    }

    @Test
    fun deleteAsset_canKeepItsTransfers() {
        val vm = detail(fromCash())
        openDeleteDialog(vm)
        compose.onNodeWithText("Undo in cash too").performClick()
        compose.onNodeWithText("The transfers stay in the cash history").assertExists()
        compose.onAllNodesWithText("Delete").onLast().performClick()
        compose.waitUntil(5_000) { vm.state.value.gone }
        assertEquals(1, runBlocking { db.cashDao().getAllOccurrences() }.size)
    }

    @Test
    fun deleteAsset_withoutTransfers_hasNoCashSwitch() {
        val id = runBlocking { portfolio.createAsset(AssetDraft("Tesouro", category("asset.fixed_income"), Currency.BRL, acquisitionDate = d("2026-01-10")), brl("5000.00"), null) }
        val vm = detail(id)
        compose.setContent { FinanceTheme { AssetDetailScreen(onBack = {}, onEdit = {}, viewModel = vm) } }
        compose.waitUntil(5_000) { vm.state.value.snapshot != null }
        compose.onNodeWithContentDescription("Delete").performClick()
        compose.onNodeWithText("Delete asset?").assertExists()
        compose.onNodeWithText("Undo in cash too").assertDoesNotExist()
    }

    /** A dark logo on a transparent background: the case the white circle is for. */
    private fun logo(): String = runBlocking {
        val file = File(context.cacheDir, "logo.png")
        val bitmap = Bitmap.createBitmap(240, 120, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawCircle(60f, 60f, 44f, Paint().apply { color = Color.rgb(240, 138, 36); isAntiAlias = true })
            drawRect(120f, 36f, 228f, 84f, Paint().apply { color = Color.rgb(27, 42, 74) })
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        images.import(Uri.fromFile(file), ImageKind.ASSET)
    }

    @Test
    fun screenshots_logoAndDelete() {
        val path = logo()
        val id = runBlocking {
            portfolio.createAsset(
                AssetDraft("CDB Aurora", category("asset.fixed_income"), Currency.BRL, institution = "Banco Aurora", acquisitionDate = d("2026-11-05"), imagePath = path),
                brl("3000.00"), null, fromCash = brl("3000.00"),
            )
        }
        val vm = detail(id)
        compose.setContent { FinanceTheme(ThemeMode.DARK) { AssetDetailScreen(onBack = {}, onEdit = {}, viewModel = vm) } }
        compose.waitUntil(5_000) { vm.state.value.snapshot != null && vm.state.value.cashMoved.isNotEmpty() }
        compose.waitForIdle()
        captureScreenRoboImage("$SCREENSHOT_DIR/asset_detail_logo.png")
        compose.onNodeWithContentDescription("Delete").performClick()
        compose.onNodeWithText("Undo in cash too").assertExists()
        compose.waitForIdle()
        captureScreenRoboImage("$SCREENSHOT_DIR/asset_delete_undo_cash.png")
    }

    @Test
    fun screenshot_editorLogo() {
        val path = logo()
        val state = runBlocking { editor().state.awaitUntil { it.categories.isNotEmpty() && !it.loading } }
        compose.snapBothThemes("asset_editor_logo") {
            AssetEditorContent(
                state.copy(name = "CDB Aurora", categoryId = category("asset.fixed_income"), investedDigits = "300000", imagePath = path, imageFile = images.file(path)),
                AssetEditorActions(),
            )
        }
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

    // Tracked assets: quantity × quote.

    private fun tracked(symbol: String = "AAPL", quantity: String = "2.5", date: String = "2026-11-02", paid: String = "800.00", category: String = "asset.stocks") = runBlocking {
        portfolio.createAsset(AssetDraft(symbol, category(category), Currency.USD, acquisitionDate = d(date), quantity = BigDecimal(quantity), ticker = symbol), usd(paid), null)
    }

    private fun price(symbol: String, date: String, price: String, fetchedAtMs: Long = 1) = runBlocking {
        db.marketDao().upsertPrices(listOf(AssetPriceEntity(symbol = symbol, date = d(date).toEpochDay(), price = price, source = PriceSource.YAHOO, fetchedAtMs = fetchedAtMs)))
    }

    /** "2.5 shares × US$ 347.19": the 1,234.56 number format, in English. */
    @Composable
    private fun DotFormat(content: @Composable () -> Unit) =
        CompositionLocalProvider(LocalFormats provides Formats.from(AppSettings(numberFormat = NumberFormatStyle.DOT_DECIMAL), Locale.US), content = content)

    private fun showDetail(vm: AssetDetailViewModel) {
        compose.setContent { FinanceTheme { DotFormat { AssetDetailScreen(onBack = {}, onEdit = {}, viewModel = vm) } } }
        compose.waitUntil(5_000) { vm.state.value.snapshot != null }
    }

    @Test
    fun tracked_detailShowsQuoteCaption() {
        val id = tracked()
        price("AAPL", "2026-11-10", "347.19")
        val vm = detail(id)
        showDetail(vm)
        compose.waitUntil(5_000) { vm.state.value.snapshot?.price != null }
        compose.onNodeWithText("2.5 shares × US$\u00a0347.19 · close of ", substring = true).assertExists()
        assertEquals(BigDecimal("2.5"), vm.state.value.held?.stripTrailingZeros())
        assertEquals(true, vm.state.value.tracked)
    }

    @Test
    fun tracked_oneShare_isSingular() {
        val id = tracked(quantity = "1")
        price("AAPL", "2026-11-10", "347.19")
        val vm = detail(id)
        showDetail(vm)
        compose.waitUntil(5_000) { vm.state.value.snapshot?.price != null }
        compose.onNodeWithText("1 share × US$\u00a0347.19 · close of ", substring = true).assertExists()
    }

    @Test
    fun tracked_crypto_captionUsesCoinCode() {
        val id = tracked("BTC-USD", "0.05", paid = "3000.00", category = "asset.cryptocurrencies")
        price("BTC-USD", "2026-11-10", "63336.84")
        val vm = detail(id)
        showDetail(vm)
        compose.waitUntil(5_000) { vm.state.value.snapshot?.price != null }
        compose.onNodeWithText("0.05 BTC × US$\u00a063,336.84", substring = true).assertExists()
    }

    @Test
    fun tracked_buy_updatesValue() {
        val id = tracked()
        price("AAPL", "2026-11-10", "347.19")
        val vm = detail(id)
        showDetail(vm)
        compose.onNodeWithText("Buy").performClick()
        compose.onNode(hasText("Quantity") and hasSetTextAction()).performTextInput("1")
        compose.onNode(hasText("Price per share") and hasSetTextAction()).performTextInput("300.00")
        compose.onNodeWithText("= US$\u00a0300.00", substring = true).assertExists()
        compose.onAllNodesWithText("Save").onLast().performClick()
        compose.waitUntil(5_000) { vm.state.value.flows.size == 2 }
        val s = vm.state.value.snapshot!!
        assertEquals(BigDecimal("3.5"), s.quantity?.stripTrailingZeros())
        assertEquals(Money.of(BigDecimal("3.5").multiply(BigDecimal("347.19")), Currency.USD), s.value)
        val buy = vm.state.value.flows.first()
        assertEquals(FlowKind.CONTRIBUTION, buy.kind)
        assertEquals(usd("300.00"), buy.amount)
    }

    @Test
    fun tracked_buyBeforeSplit_showsSplitNote() {
        val id = tracked("NVDA", date = "2026-11-02")
        price("NVDA", "2026-11-10", "121.79")
        runBlocking {
            db.marketDao().insertSplits(
                listOf(io.github.luannamorim.myfinance.data.db.entity.AssetSplitEntity(symbol = "NVDA", date = d("2026-11-10").toEpochDay(), numerator = "10", denominator = "1")),
            )
        }
        val vm = detail(id)
        showDetail(vm)
        compose.waitUntil(5_000) { vm.state.value.splits.isNotEmpty() }
        compose.onNodeWithText("Buy").performClick()
        // Today (11-12) is after the split: no note.
        compose.onNodeWithText("Includes a 10:1 split", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Date: ", substring = true).performClick()
        compose.onNodeWithText("Thursday, November 5, 2026", substring = true).performClick()
        compose.onAllNodesWithText("Save").onLast().performClick()
        compose.onNodeWithText("Includes a 10:1 split on ", substring = true).assertExists()
    }

    @Test
    fun editTracked_hidesQuantityField() {
        val id = tracked()
        val vm = editor(AssetEditorRoute(id))
        compose.setContent { FinanceTheme { AssetEditorScreen(onClose = {}, viewModel = vm) } }
        compose.waitUntil(5_000) { !vm.state.value.loading }
        compose.onNodeWithText("Name").assertExists()
        compose.onNodeWithText("Quantity (optional)").assertDoesNotExist()
    }

    @Test
    fun editManual_keepsQuantityField() {
        val id = runBlocking { portfolio.createAsset(AssetDraft("Gold", category("asset.stocks"), Currency.BRL, acquisitionDate = d("2026-11-02")), brl("100.00"), null) }
        val vm = editor(AssetEditorRoute(id))
        compose.setContent { FinanceTheme { AssetEditorScreen(onClose = {}, viewModel = vm) } }
        compose.waitUntil(5_000) { !vm.state.value.loading }
        compose.onNodeWithText("Quantity (optional)").assertExists()
    }

    @Test
    fun tracked_sellTooMany_blocksSave() {
        val id = tracked()
        price("AAPL", "2026-11-10", "347.19")
        val vm = detail(id)
        showDetail(vm)
        compose.onNodeWithText("Sell").performClick()
        compose.onNodeWithText("Sell some").assertExists()
        compose.onNodeWithText("You hold 2.5 shares on ", substring = true).assertExists()
        compose.onNode(hasText("Quantity") and hasSetTextAction()).performTextInput("3")
        compose.onNode(hasText("Price per share") and hasSetTextAction()).performTextInput("300")
        compose.onNodeWithText("You only hold 2.5 shares on that date").assertExists()
        compose.onAllNodesWithText("Save").onLast().assertIsNotEnabled()
        captureScreenRoboImage("$SCREENSHOT_DIR/asset_trade_sell_over.png")
    }

    @Test
    fun tracked_sellAll_closesPosition() {
        val id = tracked()
        price("AAPL", "2026-11-10", "347.19")
        val vm = detail(id)
        showDetail(vm)
        compose.onNodeWithText("Sell").performClick()
        compose.onNodeWithText("Sell all").performClick()
        compose.onNodeWithText("Closes the position: sells all 2.5 shares", substring = true).assertExists()
        compose.onNode(hasText("Price per share") and hasSetTextAction()).performTextInput("300.00")
        compose.onAllNodesWithText("Save").onLast().performClick()
        compose.waitUntil(5_000) { vm.state.value.closed }
        val sale = vm.state.value.flows.first()
        assertEquals(FlowKind.FULL_DISPOSAL, sale.kind)
        assertEquals(BigDecimal("2.5"), sale.quantityDelta?.stripTrailingZeros())
        assertEquals(Money.of(BigDecimal("2.5").multiply(BigDecimal("300.00")), Currency.USD), sale.amount)
    }

    @Test
    fun tracked_backdatedSell_showsError() {
        val id = tracked()
        runBlocking { portfolio.addFlow(id, FlowKind.PARTIAL_DISPOSAL, d("2026-11-10"), usd("600.00"), quantityDelta = BigDecimal("2")) }
        val vm = detail(id)
        showDetail(vm)
        vm.addFlow(FlowKind.PARTIAL_DISPOSAL, d("2026-11-05"), usd("300.00"), BigDecimal("1"), null, null, null)
        compose.waitUntil(5_000) { vm.state.value.error != null }
        compose.onNodeWithText("That's more than you held on that date.").assertExists()
        assertEquals(2, vm.state.value.flows.size)
    }

    @Test
    fun tracked_hidesValuationActions() {
        val id = tracked()
        price("AAPL", "2026-11-10", "347.19")
        val vm = detail(id)
        showDetail(vm)
        compose.onNodeWithText("Buys and sells", ignoreCase = true).assertExists()
        compose.onNodeWithText("First buy").assertExists()
        compose.onNodeWithText("Update value").assertDoesNotExist()
        compose.onNodeWithText("Contribute").assertDoesNotExist()
        compose.onNodeWithText("Withdraw").assertDoesNotExist()
        compose.onNodeWithText("Valuation history").assertDoesNotExist()
    }

    @Test
    fun screenshot_trackedDetail() {
        val id = tracked("NVDA", "100", "2026-03-02", "17700.00")
        runBlocking { portfolio.addFlow(id, FlowKind.CONTRIBUTION, d("2026-09-15"), usd("3540.00"), quantityDelta = BigDecimal("20")) }
        price("NVDA", "2026-06-30", "158.20")
        price("NVDA", "2026-09-30", "171.50")
        price("NVDA", "2026-11-12", "182.40", FakeTimeSource.at("2026-11-12", "12:05"))
        rates.let { runBlocking { it.setManual(d("2026-03-02"), BigDecimal("5.4000")); it.setManual(d("2026-09-15"), BigDecimal("5.3000")); it.setManual(d("2026-11-12"), BigDecimal("5.6143")) } }
        val state = runBlocking { detail(id).state.awaitUntil { it.flows.size == 2 && it.snapshot?.price?.date == d("2026-11-12") && it.converted?.value?.incomplete == false } }
        compose.snapBothThemes("asset_detail_quote") { AssetDetailContent(state, AssetDetailActions()) }
    }
}
