package io.github.luannamorim.myfinance.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.data.images.ImageStore
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioCalculator
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.inMemoryDb
import io.github.luannamorim.myfinance.testutil.usd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PortfolioRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = inMemoryDb(seed = true)
    private val time = FakeTimeSource()
    private val images = ImageStore(context, Dispatchers.Unconfined)
    private val repo = PortfolioRepository(db, time, BackupTrigger { }, images)

    @After fun close() = db.close()

    private fun category(key: String) = runBlocking { db.categoryDao().getAllAssetCategories().first { it.seedKey == key }.id }

    private fun draft(name: String = "Tesouro Selic", currency: Currency = Currency.BRL, key: String = "asset.fixed_income") =
        AssetDraft(name = name, categoryId = category(key), currency = currency, acquisitionDate = d("2026-01-10"))

    @Test
    fun create_writesOneInitialFlow_andOptionalValuation() = runBlocking {
        val id = repo.createAsset(draft(), brl("5000.00"), brl("5120.00"))
        val data = repo.data.first()
        val asset = data.assets.single { it.id == id }
        assertEquals(listOf(FlowKind.INITIAL), data.flowsOf(asset).map { it.kind })
        assertEquals(listOf(d("2026-11-12") to brl("5120.00")), data.valuationsOf(asset).map { it.date to it.value })

        val same = repo.createAsset(draft("CDB"), brl("1000.00"), brl("1000.00"))
        assertTrue(repo.data.first().let { it.valuationsOf(it.assets.single { a -> a.id == same }) }.isEmpty())
    }

    @Test
    fun zeroInitial_andTinyQuantity_areAllowed() = runBlocking {
        val id = repo.createAsset(draft("Car", key = "asset.vehicles").copy(quantity = BigDecimal("0.00012345")), brl("0.00"), brl("40000.00"))
        val asset = repo.asset(id)!!
        assertEquals(BigDecimal("0.00012345"), asset.quantity)
        assertEquals(brl("0.00"), repo.data.first().flowsOf(asset).single().amount)
    }

    @Test
    fun fullDisposal_closes_andBlocksLaterRecords_untilDeleted() = runBlocking {
        val id = repo.createAsset(draft(), brl("5000.00"), null)
        repo.addFlow(id, FlowKind.FULL_DISPOSAL, d("2026-07-10"), brl("5400.00"))
        val data = repo.data.first()
        val asset = data.assets.single()
        assertTrue(PortfolioCalculator.isClosed(data.flowsOf(asset), d("2026-11-12")))
        val blocked = runCatching { repo.addFlow(id, FlowKind.CONTRIBUTION, d("2026-08-01"), brl("1.00")) }
        assertTrue(blocked.exceptionOrNull() is AssetClosedException)
        assertTrue(runCatching { repo.addValuation(id, d("2026-08-01"), brl("1.00")) }.exceptionOrNull() is AssetClosedException)

        repo.deleteFlow(data.flowsOf(asset).single { it.kind == FlowKind.FULL_DISPOSAL }.id)
        repo.addFlow(id, FlowKind.CONTRIBUTION, d("2026-08-01"), brl("1.00"))
    }

    @Test
    fun sameDayValuation_isReplaced() = runBlocking {
        val id = repo.createAsset(draft(), brl("5000.00"), null)
        assertEquals(ValuationResult.ADDED, repo.addValuation(id, d("2026-03-31"), brl("5100.00")))
        assertEquals(ValuationResult.REPLACED, repo.addValuation(id, d("2026-03-31"), brl("5120.00")))
        val data = repo.data.first()
        assertEquals(listOf(brl("5120.00")), data.valuationsOf(data.assets.single()).map { it.value })
    }

    @Test
    fun contributionWithNewValue_recordsBoth() = runBlocking {
        val id = repo.createAsset(draft(), brl("5000.00"), null)
        repo.addFlow(id, FlowKind.CONTRIBUTION, d("2026-04-15"), brl("1000.00"), newValue = brl("6150.00"))
        val data = repo.data.first()
        val s = PortfolioCalculator.snapshot(data.assets.single(), data.flowsOf(data.assets.single()), data.valuationsOf(data.assets.single()), d("2026-04-15"))!!
        assertEquals(brl("6150.00"), s.value)
        assertEquals(brl("150.00"), s.gain)
    }

    @Test(expected = IllegalArgumentException::class)
    fun currencyCantChange() = runBlocking {
        val id = repo.createAsset(draft(), brl("5000.00"), null)
        repo.updateAsset(id, draft().copy(currency = Currency.USD))
    }

    @Test(expected = IllegalArgumentException::class)
    fun flowInAnotherCurrency_isRejected() = runBlocking {
        val id = repo.createAsset(draft(), brl("5000.00"), null)
        repo.addFlow(id, FlowKind.CONTRIBUTION, d("2026-04-15"), usd("10.00"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun futureValuation_isRejected() = runBlocking<Unit> {
        val id = repo.createAsset(draft(), brl("5000.00"), null)
        repo.addValuation(id, d("2026-11-13"), brl("1.00"))
    }

    @Test
    fun deleteAsset_removesEverything_includingThePhoto() = runBlocking {
        val photo = File(context.filesDir, "images/assets/a.jpg").apply { parentFile!!.mkdirs(); writeText("x") }
        val id = repo.createAsset(draft().copy(imagePath = "images/assets/a.jpg"), brl("5000.00"), brl("5100.00"))
        repo.deleteAsset(id)
        assertNull(repo.asset(id))
        assertTrue(db.portfolioDao().getAllFlows().isEmpty())
        assertTrue(db.portfolioDao().getAllValuations().isEmpty())
        assertTrue(!photo.exists())
    }
}
