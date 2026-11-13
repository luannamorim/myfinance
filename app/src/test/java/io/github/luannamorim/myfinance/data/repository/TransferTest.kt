package io.github.luannamorim.myfinance.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.data.backup.SnapshotMapper
import io.github.luannamorim.myfinance.data.images.ImageStore
import io.github.luannamorim.myfinance.domain.cash.CashCalculator
import io.github.luannamorim.myfinance.domain.cash.RecurrenceRule
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Transfers between cash and the portfolio (docs/ARCHITECTURE.md, "Transfers"). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TransferTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = inMemoryDb(seed = true)
    private val time = FakeTimeSource()
    private val cash = CashRepository(db, time, BackupTrigger { })
    private val portfolio = PortfolioRepository(db, time, BackupTrigger { }, ImageStore(context, Dispatchers.Unconfined))
    private var tesouro = 0L
    private var voo = 0L
    private var rent = 0L

    @Before
    fun setUp() = runBlocking {
        time.set("2026-11-12")
        cash.setOpening(brl("10000.00"), d("2026-11-01"))
        val categories = db.categoryDao().getAllAssetCategories()
        val fixedIncome = categories.first { it.seedKey == "asset.fixed_income" }.id
        val etfs = categories.first { it.seedKey == "asset.etfs" }.id
        tesouro = portfolio.createAsset(AssetDraft("Tesouro Selic", fixedIncome, Currency.BRL, acquisitionDate = d("2026-01-10")), brl("5000.00"), null)
        voo = portfolio.createAsset(AssetDraft("VOO", etfs, Currency.USD, acquisitionDate = d("2026-02-02")), usd("1000.00"), null)
        rent = db.categoryDao().getAll().first { it.seedKey == "expense.rent" }.id
    }

    @After fun close() = db.close()

    private fun toTesouro(amount: String = "1000.00", date: String = "2026-11-12", rule: RecurrenceRule? = null, settled: Boolean = false) =
        TransactionDraft("", null, EntryKind.TRANSFER_OUT, brl(amount), d(date), rule, alreadySettled = settled, assetId = tesouro)

    private suspend fun flows(assetId: Long) = db.portfolioDao().getFlows(assetId)
    private suspend fun balance() = CashCalculator.balance(Currency.BRL, d("2026-11-12"), cash.snapshot())
    private suspend fun contributed(assetId: Long) = portfolio.data.first().let { data ->
        PortfolioCalculator.snapshot(data.assets.first { it.id == assetId }, data.flows[assetId].orEmpty(), data.valuations[assetId].orEmpty(), d("2026-11-12"))!!.contributed
    }

    @Test
    fun recurringTransfer_confirmAddsTheContribution_undoRemovesIt() = runBlocking {
        val template = cash.create(toTesouro(rule = RecurrenceRule.MONTHLY))
        val occurrences = cash.snapshot().occurrences.filter { it.templateId == template }
        assertEquals(13, occurrences.size)
        // A blank name takes the asset's; transfers have no category.
        assertTrue(occurrences.all { it.name == "Tesouro Selic" && it.categoryId == null && it.assetId == tesouro })

        val nov = occurrences.first()
        cash.confirm(nov.id, brl("1000.00"), d("2026-11-12"), "monthly")
        val flow = flows(tesouro).single { it.occurrenceId == nov.id }
        assertEquals(FlowKind.CONTRIBUTION, flow.kind)
        assertEquals(100000L, flow.amount)
        assertEquals("monthly", flow.note)
        // Cash goes down, the asset's contributions go up: net worth doesn't move.
        assertEquals(brl("9000.00"), balance())
        assertEquals(brl("6000.00"), contributed(tesouro))

        cash.unconfirm(nov.id)
        assertTrue(flows(tesouro).none { it.occurrenceId != null })
        assertEquals(brl("10000.00"), balance())
    }

    @Test
    fun transferBetweenCurrencies_needsTheAmountTheAssetReceived() = runBlocking {
        val id = cash.create(TransactionDraft("Remessa", null, EntryKind.TRANSFER_OUT, brl("570.00"), d("2026-11-12"), null, assetId = voo))
        assertThrows(TransferAmountMissingException::class.java) { runBlocking { cash.confirm(id, brl("570.00"), d("2026-11-12"), null) } }
        assertEquals(OccurrenceState.PENDING, cash.occurrence(id)!!.state)

        cash.confirm(id, brl("570.00"), d("2026-11-12"), null, assetAmount = usd("100.00"))
        assertEquals(10000L, flows(voo).single { it.occurrenceId == id }.amount)
        assertEquals(usd("1100.00"), contributed(voo))
        assertEquals(brl("9430.00"), balance())
    }

    @Test
    fun fromTheAssetSide_contributionFromCash_andWithdrawalToCash() = runBlocking {
        portfolio.addFlow(tesouro, FlowKind.CONTRIBUTION, d("2026-11-10"), brl("2000.00"), cash = brl("2000.00"))
        portfolio.addFlow(tesouro, FlowKind.WITHDRAWAL, d("2026-11-11"), brl("500.00"), cash = brl("500.00"))
        val transfers = cash.snapshot().occurrences.filter { it.kind.isTransfer }
        assertEquals(listOf(EntryKind.TRANSFER_OUT, EntryKind.TRANSFER_IN), transfers.sortedBy { it.dueDate }.map { it.kind })
        assertTrue(transfers.all { it.state == OccurrenceState.CONFIRMED && it.standalone && it.name == "Tesouro Selic" })
        // 10.000 − 2.000 + 500
        assertEquals(brl("8500.00"), balance())

        // Deleting the flow deletes the one-time transfer with it.
        val contribution = flows(tesouro).single { it.kind == FlowKind.CONTRIBUTION }
        portfolio.deleteFlow(contribution.id)
        assertNull(cash.occurrence(contribution.occurrenceId!!))
        assertEquals(brl("10500.00"), balance())
    }

    @Test
    fun deletingTheFlowOfARecurringTransfer_putsItBackToPending() = runBlocking {
        val template = cash.create(toTesouro(rule = RecurrenceRule.MONTHLY))
        val nov = cash.snapshot().occurrences.first { it.templateId == template }
        cash.confirm(nov.id, brl("1000.00"), d("2026-11-12"), null)
        portfolio.deleteFlow(flows(tesouro).single { it.occurrenceId == nov.id }.id)
        assertEquals(OccurrenceState.PENDING, cash.occurrence(nov.id)!!.state)
        assertNull(cash.occurrence(nov.id)!!.actual)
    }

    @Test
    fun deletingAOneTimeTransfer_removesItsFlow() = runBlocking {
        val id = cash.create(toTesouro(settled = true))
        assertEquals(1, flows(tesouro).count { it.occurrenceId == id })
        cash.deleteOccurrence(id)
        assertTrue(flows(tesouro).none { it.kind == FlowKind.CONTRIBUTION })
    }

    @Test
    fun initialInvestmentFromCash_staysWhenItsTransferIsDeleted() = runBlocking {
        val fixedIncome = db.categoryDao().getAllAssetCategories().first { it.seedKey == "asset.fixed_income" }.id
        val cdb = portfolio.createAsset(AssetDraft("CDB", fixedIncome, Currency.BRL, acquisitionDate = d("2026-11-05")), brl("3000.00"), null, fromCash = brl("3000.00"))
        val initial = flows(cdb).single()
        val transfer = cash.occurrence(initial.occurrenceId!!)!!
        assertEquals(EntryKind.TRANSFER_OUT, transfer.kind)
        assertEquals(brl("7000.00"), balance())

        // It defines the asset: it can't simply be undone…
        assertThrows(InitialTransferException::class.java) { runBlocking { cash.unconfirm(transfer.id) } }
        // …but deleting the transfer keeps the initial investment (it just didn't come from cash).
        cash.deleteOccurrence(transfer.id)
        val after = flows(cdb).single()
        assertEquals(FlowKind.INITIAL, after.kind)
        assertNull(after.occurrenceId)
        assertEquals(brl("10000.00"), balance())
    }

    @Test
    fun closingThePosition_stopsRecurringTransfers_andBlocksNewOnes() = runBlocking {
        val template = cash.create(toTesouro(date = "2026-11-20", rule = RecurrenceRule.MONTHLY))
        portfolio.addFlow(tesouro, FlowKind.FULL_DISPOSAL, d("2026-11-12"), brl("5100.00"), cash = brl("5100.00"))
        assertFalse(cash.template(template)!!.active)
        assertTrue(cash.snapshot().occurrences.none { it.templateId == template && it.state == OccurrenceState.PENDING })
        assertThrows(AssetClosedException::class.java) { runBlocking { cash.create(toTesouro()) } }
        // The sale went to cash.
        assertEquals(brl("15100.00"), balance())
    }

    @Test
    fun deletingTheAsset_dropsPlannedTransfers_andKeepsDoneOnesAsHistory() = runBlocking {
        val template = cash.create(toTesouro(rule = RecurrenceRule.MONTHLY))
        val nov = cash.snapshot().occurrences.first { it.templateId == template }
        cash.confirm(nov.id, brl("1000.00"), d("2026-11-12"), null)
        val oneOff = cash.create(toTesouro(date = "2026-12-01"))

        portfolio.deleteAsset(tesouro)

        assertNull(cash.template(template))
        assertNull(cash.occurrence(oneOff))
        val kept = cash.occurrence(nov.id)!!
        assertEquals(EntryKind.TRANSFER_OUT, kept.kind)
        assertNull(kept.assetId)
        assertEquals(1, cash.snapshot().occurrences.size)
        // The money did leave cash.
        assertEquals(brl("9000.00"), balance())
    }

    @Test
    fun validation_andLockedDoneTransfers() = runBlocking<Unit> {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { cash.create(TransactionDraft("X", rent, EntryKind.TRANSFER_OUT, brl("1.00"), d("2026-11-12"), null, assetId = tesouro)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { cash.create(TransactionDraft("Rent", rent, EntryKind.EXPENSE, brl("1.00"), d("2026-11-12"), null, assetId = tesouro)) }
        }
        assertThrows(TransferBeforeAcquisitionException::class.java) {
            runBlocking { cash.create(toTesouro(date = "2025-12-31", settled = true)) }
        }
        val done = cash.create(toTesouro(settled = true))
        assertThrows(TransferLockedException::class.java) {
            runBlocking { cash.updateStandalone(done, toTesouro().copy(assetId = voo, assetAmount = usd("1.00"))) }
        }
        // Editing the description is fine.
        cash.updateStandalone(done, toTesouro(settled = true).copy(description = "Extra"))
        assertEquals("Extra", cash.occurrence(done)!!.description)
        assertEquals(1, flows(tesouro).count { it.occurrenceId == done })
    }

    @Test
    fun backup_keepsTheLinks() = runBlocking {
        val id = cash.create(toTesouro(settled = true))
        val data = SnapshotMapper.export(db)
        SnapshotMapper.replaceAll(db, data)
        val restored = cash.occurrence(id)!!
        assertEquals(tesouro, restored.assetId)
        assertNull(restored.categoryId)
        assertEquals(id, flows(tesouro).single { it.kind == FlowKind.CONTRIBUTION }.occurrenceId)
    }
}
