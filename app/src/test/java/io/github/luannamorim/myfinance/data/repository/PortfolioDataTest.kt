package io.github.luannamorim.myfinance.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.data.db.entity.AssetFlowEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetValuationEntity
import io.github.luannamorim.myfinance.data.images.ImageStore
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioData
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.inMemoryDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The portfolio stream every screen observes must survive deletions (its tables update one at a time). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PortfolioDataTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = inMemoryDb(seed = true)
    private val time = FakeTimeSource()
    private val portfolio = PortfolioRepository(db, time, BackupTrigger { }, ImageStore(context, Dispatchers.Unconfined))

    @Test
    fun deletingAssets_whileObserved_neverBreaksTheStream() = runBlocking {
        time.set("2026-11-12")
        val category = db.categoryDao().getAllAssetCategories().first { it.seedKey == "asset.fixed_income" }.id
        val ids = (1..8).map { i ->
            portfolio.createAsset(AssetDraft("Asset $i", category, Currency.BRL, acquisitionDate = d("2026-01-10")), brl("1000.00"), brl("1100.00")).also { id ->
                portfolio.addFlow(id, FlowKind.CONTRIBUTION, d("2026-06-01"), brl("100.00"))
            }
        }
        val latest = MutableStateFlow<PortfolioData?>(null)
        val errors = mutableListOf<Throwable>()
        val failed = MutableStateFlow(false)
        val collector = launch(Dispatchers.Default) {
            portfolio.data.catch { errors += it; failed.value = true }.collect { latest.value = it }
        }
        try {
            withTimeout(5_000) { latest.first { it?.assets?.size == 8 } }
            ids.forEach { id ->
                portfolio.deleteAsset(id)
                withTimeout(5_000) {
                    combine(latest, failed) { data, f -> f || data?.assets?.none { it.id == id } == true }.first { it }
                }
                if (failed.value) return@forEach
            }
        } finally {
            collector.cancel()
        }
        assertTrue("the stream failed: ${errors.firstOrNull()}", errors.isEmpty())
        assertEquals(0, latest.value!!.assets.size)
    }

    @Test
    fun flowsAndValuations_ofAnAssetNotInTheList_areLeftOut() {
        val data = portfolioData(
            assets = emptyList(),
            flows = listOf(AssetFlowEntity(assetId = 9, kind = FlowKind.INITIAL, date = 0, amount = 100, createdAtMs = 0)),
            valuations = listOf(AssetValuationEntity(assetId = 9, date = 0, value = 100, createdAtMs = 0)),
            categories = emptyList(),
        )
        assertTrue(data.flows.isEmpty() && data.valuations.isEmpty())
    }
}
