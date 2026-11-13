package io.github.luannamorim.myfinance.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.data.prefs.RateError
import io.github.luannamorim.myfinance.data.prefs.RateStateStore
import io.github.luannamorim.myfinance.data.rates.RateClient
import io.github.luannamorim.myfinance.domain.cash.RecurrenceRule
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.RateSource
import io.github.luannamorim.myfinance.platform.rates.RateRefreshWorker
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.inMemoryDb
import io.github.luannamorim.myfinance.testutil.testSettings
import io.github.luannamorim.myfinance.testutil.usd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.Files
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ExchangeRateRepositoryTest {

    private class FakeClient : RateClient {
        val requests = mutableListOf<Pair<LocalDate, LocalDate>>()
        var response: (LocalDate, LocalDate) -> String = { _, _ -> "[]" }
        override suspend fun fetch(from: LocalDate, to: LocalDate): String {
            requests += from to to
            return response(from, to)
        }
    }

    private val db = inMemoryDb(seed = true)
    private val time = FakeTimeSource()
    private val client = FakeClient()
    private val settings = testSettings()
    private val store = RateStateStore(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            File(Files.createTempDirectory("rates").toFile(), "rate_state.preferences_pb")
        },
    )
    private val repo = ExchangeRateRepository(db, client, store, settings, time, BackupTrigger { })
    private val cash = CashRepository(db, time, BackupTrigger { })

    @After fun close() = db.close()

    private fun bcb(vararg days: Pair<String, String>) = days.joinToString(",", "[", "]") { (date, value) ->
        val (y, m, dd) = date.split("-")
        """{"data":"$dd/$m/$y","valor":"$value"}"""
    }

    @Test
    fun firstRefresh_withoutRecords_fetchesLast30Days() = runBlocking {
        client.response = { _, _ -> bcb("2026-11-11" to "5.5606", "2026-11-12" to "5.6143") }
        assertEquals(RefreshResult.Updated(2), repo.refresh())
        assertEquals(listOf(d("2026-10-13") to d("2026-11-12")), client.requests)
        assertNotNull(store.current().lastFetchMs)
        assertEquals(BigDecimal("5.614300"), repo.rates.first().latest()!!.rate)
    }

    @Test
    fun firstRefresh_reachesBackToTheEarliestRecord() = runBlocking {
        val rent = db.categoryDao().getAll().first { it.seedKey == "expense.rent" }.id
        cash.create(TransactionDraft("Old", rent, EntryKind.EXPENSE, usd("10.00"), d("2025-03-02"), null))
        repo.refresh()
        assertEquals(listOf(d("2025-02-23") to d("2026-11-12")), client.requests)
    }

    @Test
    fun laterRefresh_continuesFromLastRate_andWeekend404IsUpToDate() = runBlocking {
        client.response = { _, _ -> bcb("2026-11-12" to "5.6143") }
        repo.refresh()
        time.set("2026-11-14") // Saturday, more than 6 h later
        client.response = { _, _ -> "[]" }
        assertEquals(RefreshResult.UpToDate, repo.refresh())
        assertEquals(d("2026-11-13") to d("2026-11-14"), client.requests.last())
    }

    @Test
    fun olderRecord_triggersBackfill() = runBlocking {
        client.response = { _, _ -> bcb("2026-10-13" to "5.40", "2026-11-12" to "5.6143") }
        repo.refresh()
        val rent = db.categoryDao().getAll().first { it.seedKey == "expense.rent" }.id
        cash.create(TransactionDraft("Old", rent, EntryKind.EXPENSE, usd("10.00"), d("2026-01-10"), RecurrenceRule.MONTHLY).copy(rule = null))
        time.set("2026-11-13", "18:00")
        client.requests.clear()
        repo.refresh(force = true)
        assertTrue(client.requests.contains(d("2026-01-03") to d("2026-10-12")))
    }

    @Test
    fun manualRates_coexistWithBcb() = runBlocking {
        client.response = { _, _ -> bcb("2026-11-12" to "5.6143") }
        repo.refresh()
        repo.setManual(d("2026-11-12"), BigDecimal("5.70"))
        assertEquals(RateSource.MANUAL, repo.rates.first().rateOn(d("2026-11-12"))!!.source)
        repo.deleteManual(d("2026-11-12"))
        assertEquals(RateSource.BCB, repo.rates.first().rateOn(d("2026-11-12"))!!.source)
    }

    @Test
    fun switchOff_disablesAutomaticRuns_butUpdateStillFetches() = runBlocking {
        settings.update { it.copy(autoFetchRates = false) }
        assertEquals(RefreshResult.Disabled, repo.refresh())
        assertTrue(client.requests.isEmpty())
        repo.refresh(force = true)
        assertEquals(1, client.requests.size)
    }

    @Test
    fun throttle_appliesToAutomaticRunsOnly() = runBlocking {
        repo.refresh()
        assertEquals(RefreshResult.Throttled, repo.refresh())
        repo.refresh(force = true)
        assertEquals(2, client.requests.size)
    }

    @Test
    fun networkFailure_keepsRates_andRecordsError() = runBlocking {
        client.response = { _, _ -> bcb("2026-11-12" to "5.6143") }
        repo.refresh()
        time.set("2026-11-13", "18:00")
        client.response = { _, _ -> throw IOException("offline") }
        assertEquals(RefreshResult.Failed(RateError.NETWORK), repo.refresh(force = true))
        assertEquals(RateError.NETWORK, store.current().lastError)
        assertEquals(1, db.rateDao().getAll().size)
    }

    @Test
    fun windows_areSplitIntoTenYears() {
        val parts = ExchangeRateRepository.split(d("2004-01-01"), d("2026-11-12"))
        assertEquals(3, parts.size)
        assertEquals(d("2013-12-31"), parts[0].second)
        assertEquals(d("2014-01-01"), parts[1].first)
    }

    @Test
    fun worker_retriesOnNetworkFailure_andFailsOnBadFormat() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                RateRefreshWorker(appContext, workerParameters, repo)
        }
        client.response = { _, _ -> throw IOException("offline") }
        val worker = TestListenableWorkerBuilder<RateRefreshWorker>(context).setWorkerFactory(factory).build()
        assertEquals(ListenableWorker.Result.retry(), worker.doWork())

        client.response = { _, _ -> "<html>" }
        time.set("2026-11-13", "18:00")
        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }
}
