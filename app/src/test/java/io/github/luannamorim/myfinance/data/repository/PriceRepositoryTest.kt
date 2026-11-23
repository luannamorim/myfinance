package io.github.luannamorim.myfinance.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.data.db.entity.AssetFlowEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetPriceEntity
import io.github.luannamorim.myfinance.data.prefs.PriceError
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.PriceSource
import io.github.luannamorim.myfinance.testutil.FakeQuoteClient
import io.github.luannamorim.myfinance.testutil.NOT_FOUND_BODY
import io.github.luannamorim.myfinance.testutil.asset
import io.github.luannamorim.myfinance.testutil.chartJson
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.day
import io.github.luannamorim.myfinance.testutil.inMemoryDb
import io.github.luannamorim.myfinance.testutil.testPrices
import io.github.luannamorim.myfinance.testutil.testSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PriceRepositoryTest {

    private val db = inMemoryDb(seed = true)
    private val time = FakeTimeSource()
    private val settings = testSettings()
    private val client = FakeQuoteClient()
    private var backups = 0
    private val repo = testPrices(db, settings, time, client, BackupTrigger { backups++ })
    private val market = db.marketDao()
    private val portfolio = db.portfolioDao()

    @After fun close() = db.close()

    private fun resource(name: String) = javaClass.getResource("/yahoo/$name")!!.readText()

    private suspend fun category() = db.categoryDao().getAllAssetCategories().first().id

    /** A tracked asset on [symbol] bought on [bought]; closed on [closedOn] when given. */
    private suspend fun track(symbol: String, bought: String = "2026-02-02", closedOn: String? = null): Long {
        val id = portfolio.insertAsset(asset(category(), name = symbol, currency = Currency.USD, ticker = symbol))
        portfolio.insertFlow(AssetFlowEntity(assetId = id, kind = FlowKind.INITIAL, date = day(bought), amount = 100_00, quantityDelta = "1", createdAtMs = 0))
        if (closedOn != null) {
            portfolio.insertFlow(AssetFlowEntity(assetId = id, kind = FlowKind.FULL_DISPOSAL, date = day(closedOn), amount = 120_00, quantityDelta = "1", createdAtMs = 0))
        }
        return id
    }

    private suspend fun storePrice(symbol: String, date: String, price: String) =
        market.upsertPrices(listOf(AssetPriceEntity(symbol = symbol, date = day(date), price = price, source = PriceSource.YAHOO, fetchedAtMs = 0)))

    private suspend fun prices(symbol: String) = market.listPrices().filter { it.symbol == symbol }.associate { LocalDate.ofEpochDay(it.date).toString() to it.price }

    @Test
    fun nothingTracked_noRequest() = runBlocking {
        val id = portfolio.insertAsset(asset(category()))
        portfolio.insertFlow(AssetFlowEntity(assetId = id, kind = FlowKind.INITIAL, date = day("2026-02-02"), amount = 100_00, createdAtMs = 0))
        assertEquals(PriceRefreshResult.NothingTracked, repo.refresh())
        assertEquals(PriceRefreshResult.NothingTracked, repo.refresh(force = true))
        assertTrue(client.chartRequests.isEmpty())
        assertNull(repo.state.first().lastAttemptMs)
    }

    @Test
    fun firstFetch_startsAWeekBeforeEarliestFlow() = runBlocking {
        track("AAPL", bought = "2026-02-02")
        client.chart = { s, _ -> chartJson(s, "2026-11-11" to "230.10") }
        assertEquals(PriceRefreshResult.Updated(1), repo.refresh())
        assertEquals(listOf("AAPL" to d("2026-01-26")), client.chartRequests)
    }

    @Test
    fun nextFetch_startsAWeekBeforeLastPrice() = runBlocking {
        track("AAPL")
        storePrice("AAPL", "2026-01-26", "200")
        storePrice("AAPL", "2026-11-12", "230.1")
        time.set("2026-11-13", "11:00")
        client.chart = { s, _ -> chartJson(s, "2026-11-12" to "230.10", "2026-11-13" to "231.00") }
        assertEquals(PriceRefreshResult.Updated(1), repo.refresh())
        assertEquals(listOf("AAPL" to d("2026-11-05")), client.chartRequests)
    }

    @Test
    fun sameDayUpdate_noBackup_newDay_backup() = runBlocking {
        track("AAPL")
        client.chart = { s, _ -> chartJson(s, "2026-11-12" to "230.10") }
        assertEquals(PriceRefreshResult.Updated(1), repo.refresh())
        assertEquals(1, backups)
        assertEquals(mapOf("2026-11-12" to "230.1"), prices("AAPL"))

        client.chart = { s, _ -> chartJson(s, "2026-11-12" to "231.50") }
        assertEquals(PriceRefreshResult.UpToDate, repo.refresh(force = true))
        assertEquals(mapOf("2026-11-12" to "231.5"), prices("AAPL"))
        assertEquals(1, backups)

        time.set("2026-11-13")
        client.chart = { s, _ -> chartJson(s, "2026-11-12" to "231.50", "2026-11-13" to "232.00") }
        assertEquals(PriceRefreshResult.Updated(1), repo.refresh(force = true))
        assertEquals(2, backups)
    }

    @Test
    fun rawPricesStored() = runBlocking {
        track("NVDA", bought = "2024-06-03")
        client.chart = { s, _ ->
            chartJson(
                s, "2024-06-06" to "120.99800109863281", "2024-06-07" to "120.88800048828125", "2024-06-10" to "121.79000091552734",
                splits = listOf("2024-06-10" to "10.0:1.0"),
            )
        }
        assertEquals(PriceRefreshResult.Updated(4), repo.refresh())
        assertEquals(mapOf("2024-06-06" to "1209.98", "2024-06-07" to "1208.88", "2024-06-10" to "121.79"), prices("NVDA"))
        val split = market.splitsOf("NVDA").single()
        assertEquals(day("2024-06-10"), split.date)
        assertEquals(0, BigDecimal(split.numerator).compareTo(BigDecimal.TEN))
    }

    @Test
    fun newSplit_triggersFullRefetch() = runBlocking {
        track("NVDA", bought = "2024-06-03")
        storePrice("NVDA", "2024-05-28", "1100")
        storePrice("NVDA", "2024-06-07", "1208.88")
        client.chart = { s, _ ->
            chartJson(s, "2024-06-07" to "120.88800048828125", "2024-06-10" to "121.79000091552734", splits = listOf("2024-06-10" to "10:1"))
        }
        // New rows: the 06-10 close and the split.
        assertEquals(PriceRefreshResult.Updated(2), repo.refresh())
        assertEquals(listOf("NVDA" to d("2024-05-31"), "NVDA" to d("2024-05-27")), client.chartRequests)
        assertEquals(1, market.splitsOf("NVDA").size)
        assertEquals("1208.88", prices("NVDA")["2024-06-07"])

        // The split is stored now: the next run doesn't refetch.
        client.chartRequests.clear()
        repo.refresh(force = true)
        assertEquals(listOf("NVDA" to d("2024-06-03")), client.chartRequests)
    }

    @Test
    fun unknownSymbol_isolated() = runBlocking {
        track("ZZZZ")
        track("AAPL")
        client.chart = { s, _ -> if (s == "ZZZZ") NOT_FOUND_BODY else chartJson(s, "2026-11-11" to "230.10") }
        assertEquals(PriceRefreshResult.Failed(PriceError.NOT_FOUND), repo.refresh())
        assertEquals(mapOf("2026-11-11" to "230.1"), prices("AAPL"))
        val state = repo.state.first()
        assertEquals(time.nowMs, state.lastFetchMs)
        assertEquals(PriceError.NOT_FOUND, state.lastError)
        assertEquals(1, backups)
    }

    @Test
    fun badResponse_isFormatError() = runBlocking {
        track("AAPL")
        client.chart = { _, _ -> "<html>" }
        assertEquals(PriceRefreshResult.Failed(PriceError.FORMAT), repo.refresh())
        assertNull(repo.state.first().lastFetchMs)
    }

    @Test
    fun olderBuyOnSameTicker_fetchesFromItsDate() = runBlocking {
        track("AAPL", bought = "2026-11-01")
        storePrice("AAPL", "2026-11-01", "220")
        storePrice("AAPL", "2026-11-11", "230.1")
        track("AAPL", bought = "2026-06-02")
        client.chart = { s, _ -> chartJson(s, "2026-06-02" to "190.00", "2026-11-11" to "230.10") }
        assertEquals(PriceRefreshResult.Updated(1), repo.refresh())
        assertEquals(listOf("AAPL" to d("2026-05-26")), client.chartRequests)
    }

    @Test
    fun pricesCoverEarliestBuy_fetchesFromLastPrice() = runBlocking {
        // Bought on a Monday: the first fetch's window starts on a Monday too, but its first close may be later.
        track("AAPL", bought = "2026-11-02")
        storePrice("AAPL", "2026-10-27", "215")
        storePrice("AAPL", "2026-11-11", "230.1")
        client.chart = { s, _ -> chartJson(s, "2026-11-11" to "230.10") }
        repo.refresh()
        assertEquals(listOf("AAPL" to d("2026-11-04")), client.chartRequests)
    }

    @Test
    fun absurdTimestamp_isFormatError() = runBlocking {
        track("AAPL")
        client.chart = { s, _ ->
            """{"chart":{"result":[{"meta":{"currency":"USD","symbol":"$s","exchangeTimezoneName":"America/New_York"},""" +
                """"timestamp":[99999999999999999],"indicators":{"quote":[{"close":[1.0]}]}}],"error":null}}"""
        }
        assertEquals(PriceRefreshResult.Failed(PriceError.FORMAT), repo.refresh())
    }

    @Test
    fun networkError_stopsRun() = runBlocking {
        track("AAPL")
        track("MSFT")
        storePrice("MSFT", "2026-11-10", "500")
        client.chart = { s, _ -> if (s == "AAPL") throw IOException("offline") else chartJson(s, "2026-11-11" to "510.00") }
        assertEquals(PriceRefreshResult.Failed(PriceError.NETWORK), repo.refresh())
        assertEquals(listOf("AAPL"), client.chartRequests.map { it.first })
        assertEquals(mapOf("2026-11-10" to "500"), prices("MSFT"))
        val state = repo.state.first()
        assertEquals(PriceError.NETWORK, state.lastError)
        assertEquals(time.nowMs, state.lastAttemptMs)
        assertNull(state.lastFetchMs)
    }

    @Test
    fun twoAssetsSameTicker_oneRequest() = runBlocking {
        track("AAPL", bought = "2026-03-01")
        track("AAPL", bought = "2026-02-02")
        client.chart = { s, _ -> chartJson(s, "2026-11-11" to "230.10") }
        assertEquals(PriceRefreshResult.Updated(1), repo.refresh())
        assertEquals(listOf("AAPL" to d("2026-01-26")), client.chartRequests)
    }

    @Test
    fun closedPosition_alreadyPriced_skipped() = runBlocking {
        track("AAPL", closedOn = "2026-06-01")
        storePrice("AAPL", "2026-06-01", "200")
        client.chart = { s, _ -> chartJson(s, "2026-11-11" to "230.10") }
        assertEquals(PriceRefreshResult.UpToDate, repo.refresh())
        assertTrue(client.chartRequests.isEmpty())
    }

    @Test
    fun throttle_15Minutes() = runBlocking {
        track("AAPL")
        client.chart = { s, _ -> chartJson(s, "2026-11-11" to "230.10") }
        repo.refresh()
        time.nowMs += 10 * 60 * 1000L
        assertEquals(PriceRefreshResult.Throttled, repo.refresh())
        assertEquals(PriceRefreshResult.UpToDate, repo.refresh(force = true))
        time.nowMs += 15 * 60 * 1000L
        assertEquals(PriceRefreshResult.UpToDate, repo.refresh())
        assertEquals(3, client.chartRequests.size)
    }

    @Test
    fun switchOff_disablesAutomaticOnly() = runBlocking {
        track("AAPL")
        settings.update { it.copy(autoFetchPrices = false) }
        client.chart = { s, _ -> chartJson(s, "2026-11-11" to "230.10") }
        assertEquals(PriceRefreshResult.Disabled, repo.refresh())
        assertTrue(client.chartRequests.isEmpty())
        assertEquals(PriceRefreshResult.Updated(1), repo.refresh(force = true))
    }

    @Test
    fun preview_returnsLastPriceAndSplitsSince() = runBlocking {
        client.chart = { _, _ -> resource("nvda_three_bars.json") }
        val preview = repo.preview("NVDA", d("2024-06-03"))
        assertEquals("NVDA", preview.symbol)
        assertEquals("NVIDIA Corporation", preview.name)
        assertEquals(d("2024-06-10"), preview.price.date)
        assertEquals(0, BigDecimal("121.79").compareTo(preview.price.price))
        assertEquals(listOf(d("2024-06-10")), preview.splitsSince.map { it.date })
        assertEquals(listOf("NVDA" to d("2024-05-27")), client.chartRequests)
        assertTrue(market.listPrices().isEmpty())
        assertTrue(market.listSplits().isEmpty())
    }

    @Test
    fun preview_splitOnFromDate_isNotSince() = runBlocking {
        client.chart = { _, _ -> resource("nvda_three_bars.json") }
        assertTrue(repo.preview("NVDA", d("2024-06-10")).splitsSince.isEmpty())
    }

    @Test
    fun preview_dayWithoutCandle_usesLastCloseBefore() = runBlocking {
        // Saturday: Yahoo has no candle from the purchase date on, only before it.
        time.set("2026-11-21")
        client.chart = { s, from -> if (from <= d("2026-11-20")) chartJson(s, "2026-11-20" to "230.10") else resource("weekend.json") }
        val preview = repo.preview("AAPL", d("2026-11-21"))
        assertEquals(d("2026-11-20"), preview.price.date)
        assertEquals(0, BigDecimal("230.1").compareTo(preview.price.price))
        assertEquals(listOf("AAPL" to d("2026-11-14")), client.chartRequests)
    }

    /** A crypto chart (UTC dates) with one bar per (UTC date at 00:00, close). */
    private fun cryptoJson(symbol: String, vararg bars: Pair<String, String>): String {
        val timestamps = bars.joinToString(",") { LocalDate.parse(it.first).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond().toString() }
        val closes = bars.joinToString(",") { it.second }
        return """{"chart":{"result":[{"meta":{"currency":"USD","symbol":"$symbol","instrumentType":"CRYPTOCURRENCY",""" +
            """"exchangeTimezoneName":"UTC"},"timestamp":[$timestamps],"indicators":{"quote":[{"close":[$closes]}]}}],"error":null}}"""
    }

    @Test
    fun cryptoCandleAheadOfLocalDate_storedUnderToday() = runBlocking {
        track("BTC-USD", bought = "2026-11-02")
        // 22:00 in São Paulo is already 01:00 UTC of the next day: the live candle is dated "tomorrow".
        time.set("2026-11-23", "22:00")
        client.chart = { s, _ -> cryptoJson(s, "2026-11-23" to "62000", "2026-11-24" to "63000") }
        repo.refresh()
        assertEquals(mapOf("2026-11-23" to "63000"), prices("BTC-USD"))
        val data = io.github.luannamorim.myfinance.domain.market.MarketData(
            mapOf(
                "BTC-USD" to io.github.luannamorim.myfinance.domain.market.SymbolHistory(
                    market.listPrices().map { io.github.luannamorim.myfinance.domain.market.PricePoint(LocalDate.ofEpochDay(it.date), BigDecimal(it.price)) },
                    emptyList(),
                ),
            ),
        )
        assertEquals(0, BigDecimal("63000").compareTo(data.priceOn("BTC-USD", d("2026-11-23"))!!.price))

        // After local midnight the days get their real dates back.
        time.set("2026-11-24", "10:00")
        client.chart = { s, _ -> cryptoJson(s, "2026-11-23" to "62500", "2026-11-24" to "63100") }
        repo.refresh(force = true)
        assertEquals(mapOf("2026-11-23" to "62500", "2026-11-24" to "63100"), prices("BTC-USD"))
    }

    @Test
    fun farFutureBar_droppedAndNextWindowNormal() = runBlocking {
        track("AAPL")
        storePrice("AAPL", "2026-01-26", "200")
        val seconds = LocalDate.parse("2026-11-11").atTime(9, 30).atZone(java.time.ZoneId.of("America/New_York")).toEpochSecond()
        client.chart = { s, _ ->
            """{"chart":{"result":[{"meta":{"currency":"USD","symbol":"$s","exchangeTimezoneName":"America/New_York"},""" +
                """"timestamp":[$seconds,${seconds * 1000}],"indicators":{"quote":[{"close":[230.1,231]}]}}],"error":null}}"""
        }
        assertEquals(PriceRefreshResult.Updated(1), repo.refresh())
        assertEquals(mapOf("2026-01-26" to "200", "2026-11-11" to "230.1"), prices("AAPL"))
        client.chartRequests.clear()
        repo.refresh(force = true)
        assertEquals(listOf("AAPL" to d("2026-11-04")), client.chartRequests)
    }

    @Test
    fun assetDeletedDuringRefresh_pricesPruned() = runBlocking {
        val id = track("AAPL")
        client.chart = { s, _ ->
            db.openHelper.writableDatabase.execSQL("DELETE FROM asset WHERE id = $id")
            chartJson(s, "2026-11-11" to "230.10")
        }
        repo.refresh()
        assertTrue(market.listPrices().isEmpty())
    }

    @Test(expected = TickerNotFoundException::class)
    fun preview_unknown_throws(): Unit = runBlocking {
        repo.preview("ZZZZ", d("2024-06-03"))
        Unit
    }

    @Test
    fun search_parsesMatches() = runBlocking {
        client.search = { resource("search_nvd.json") }
        assertTrue(repo.search("nvd").any { it.symbol == "NVDA" })
        assertEquals(listOf("nvd"), client.searchRequests)
    }
}
