package io.github.luannamorim.myfinance.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.data.images.ImageStore
import io.github.luannamorim.myfinance.domain.cash.RecurrenceRule
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.data.db.entity.AssetPriceEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetSplitEntity
import io.github.luannamorim.myfinance.domain.model.PriceSource
import io.github.luannamorim.myfinance.testutil.asset
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.inMemoryDb
import io.github.luannamorim.myfinance.testutil.usd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ExportRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = inMemoryDb(seed = true)
    private val time = FakeTimeSource()

    @After fun close() = db.close()

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val files = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                files[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return files
    }

    @Test
    fun exportsAllFiles_withHeaders_currencies_andSafeText() = runBlocking {
        val cash = CashRepository(db, time, BackupTrigger { })
        val rent = db.categoryDao().getAll().first { it.seedKey == "expense.rent" }.id
        val subs = db.categoryDao().getAll().first { it.seedKey == "expense.subscriptions" }.id
        cash.setOpening(brl("10000.00"), d("2026-11-01"))
        val template = cash.create(TransactionDraft("Rent, apt 2", rent, EntryKind.EXPENSE, brl("2500.00"), d("2026-11-05"), RecurrenceRule.MONTHLY))
        val nov = cash.snapshot().occurrences.first { it.templateId == template }
        cash.confirm(nov.id, brl("2550.00"), d("2026-11-10"), "=HYPERLINK(\"x\")")
        val cloud = cash.create(TransactionDraft("Cloud", subs, EntryKind.EXPENSE, usd("20.00"), d("2026-11-12"), null))
        cash.confirm(cloud, brl("115.00"), d("2026-11-12"), null)
        val portfolio = PortfolioRepository(db, time, BackupTrigger { }, ImageStore(context, Dispatchers.Unconfined))
        val etfs = db.categoryDao().getAllAssetCategories().first { it.seedKey == "asset.etfs" }.id
        val voo = portfolio.createAsset(AssetDraft("VOO", etfs, Currency.USD, acquisitionDate = d("2026-02-02")), usd("1000.00"), usd("1100.00"))
        portfolio.addFlow(voo, FlowKind.CONTRIBUTION, d("2026-11-11"), usd("100.00"), cash = brl("570.00"))

        val out = ByteArrayOutputStream()
        ExportRepository(context, db, Dispatchers.Unconfined).export(out)
        val files = unzip(out.toByteArray())

        assertEquals(ExportRepository.FILES, files.keys.toList())
        val transactions = files.getValue("transactions.csv").split("\r\n")
        assertEquals("id,type,name,category,asset,status,due_date,expected_amount,currency,actual_date,actual_amount,actual_currency,recurring,description,note", transactions[0])
        val paidRent = transactions.first { it.contains("CONFIRMED") && it.contains("Rent, apt 2") }
        assertTrue(paidRent, paidRent.contains("\"Rent, apt 2\",Rent,,CONFIRMED,2026-11-05,2500.00,BRL,2026-11-10,2550.00,BRL,yes,,\"'=HYPERLINK(\"\"x\"\")\""))
        val cloudRow = transactions.first { it.contains("Cloud") }
        assertTrue(cloudRow, cloudRow.contains("Subscriptions,,CONFIRMED,2026-11-12,20.00,USD,2026-11-12,115.00,BRL,no"))
        // A contribution from cash: the transfer (in BRL) and the flow (in USD) point at each other.
        val transfer = transactions.first { it.contains("TRANSFER_OUT") }
        assertTrue(transfer, transfer.contains(",TRANSFER_OUT,VOO,,VOO,CONFIRMED,2026-11-11,570.00,BRL,2026-11-11,570.00,BRL,no"))
        val transferId = transfer.substringBefore(',')
        assertTrue(files.getValue("cash_adjustments.csv").contains("OPENING,2026-11-01,10000.00,BRL"))
        assertTrue(files.getValue("assets.csv").contains("VOO,ETFs,INVESTMENT,USD"))
        assertTrue(files.getValue("asset_flows.csv").contains("VOO,INITIAL,in,2026-02-02,1000.00,USD"))
        assertTrue(files.getValue("asset_flows.csv").contains("VOO,CONTRIBUTION,in,2026-11-11,100.00,USD,,,$transferId\r\n"))
        assertTrue(files.getValue("valuations.csv").contains("VOO,2026-11-12,1100.00,USD"))
        assertEquals("date,base,quote,rate,source\r\n", files.getValue("exchange_rates.csv"))
    }

    @Test
    fun exportsTicker_prices_andSplits() = runBlocking {
        val stocks = db.categoryDao().getAllAssetCategories().first { it.seedKey == "asset.etfs" }.id
        db.portfolioDao().insertAsset(asset(stocks, "Apple", Currency.USD, ticker = "AAPL"))
        db.marketDao().upsertPrices(listOf(AssetPriceEntity(symbol = "AAPL", date = d("2026-11-20").toEpochDay(), price = "347.19", source = PriceSource.YAHOO, fetchedAtMs = 1)))
        db.marketDao().insertSplits(listOf(AssetSplitEntity(symbol = "AAPL", date = d("2026-06-01").toEpochDay(), numerator = "4", denominator = "1")))

        val out = ByteArrayOutputStream()
        ExportRepository(context, db, Dispatchers.Unconfined).export(out)
        val files = unzip(out.toByteArray())

        assertEquals(8, files.size)
        val assets = files.getValue("assets.csv").split("\r\n")
        assertTrue(assets[0], assets[0].endsWith(",notes,ticker"))
        assertTrue(assets.first { it.contains("Apple") }.endsWith(",AAPL"))
        assertEquals("date,symbol,price,source\r\n2026-11-20,AAPL,347.19,YAHOO\r\n", files.getValue("asset_prices.csv"))
        assertEquals("date,symbol,numerator,denominator\r\n2026-06-01,AAPL,4,1\r\n", files.getValue("asset_splits.csv"))
    }
}
