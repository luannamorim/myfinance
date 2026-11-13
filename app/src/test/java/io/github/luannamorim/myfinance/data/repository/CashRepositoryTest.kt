package io.github.luannamorim.myfinance.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.domain.cash.CashCalculator
import io.github.luannamorim.myfinance.domain.cash.RecurrenceRule
import io.github.luannamorim.myfinance.domain.model.AdjustmentKind
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.Occurrence
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.inMemoryDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class CashRepositoryTest {

    private val db = inMemoryDb(seed = true)
    private val time = FakeTimeSource()
    private var changes = 0
    private val repo = CashRepository(db, time, BackupTrigger { changes++ })
    private var rent = 0L
    private var salary = 0L

    @Before
    fun setUp() = runBlocking {
        val categories = db.categoryDao().getAll()
        rent = categories.first { it.seedKey == "expense.rent" }.id
        salary = categories.first { it.seedKey == "income.salary" }.id
        time.set("2026-11-12")
    }

    @After
    fun close() = db.close()

    private fun monthly(first: String, amount: String = "2500.00") =
        TransactionDraft("Rent", rent, EntryKind.EXPENSE, brl(amount), d(first), RecurrenceRule.MONTHLY)

    private suspend fun rows(templateId: Long): List<Occurrence> =
        repo.snapshot().occurrences.filter { it.templateId == templateId }.sortedBy { it.scheduledDate }

    private suspend fun dates(templateId: Long) = rows(templateId).map { it.dueDate.toString() }

    @Test
    fun create_generatesThirteenMonths_andIsIdempotent() = runBlocking {
        val id = repo.create(monthly("2026-11-05"))
        assertEquals(13, rows(id).size)
        assertEquals("2026-11-05", dates(id).first())
        assertEquals("2027-11-05", dates(id).last())
        repo.ensureGenerated()
        repo.ensureGenerated()
        assertEquals(13, rows(id).size)
    }

    @Test
    fun create_withPastFirstDate_doesNotBackfill() = runBlocking {
        val id = repo.create(monthly("2026-09-05"))
        assertEquals("2026-11-05", dates(id).first())
        assertEquals(d("2026-09-05"), repo.template(id)!!.startDate)
    }

    @Test
    fun ensureGenerated_extendsHorizonAsTimePasses() = runBlocking {
        val id = repo.create(monthly("2026-11-05"))
        time.set("2027-01-03")
        repo.ensureGenerated()
        assertEquals("2028-01-05", dates(id).last())
        assertEquals(15, rows(id).size)
    }

    @Test
    fun amountEdit_keepsPastConfirmedAndOverdue() = runBlocking {
        time.set("2026-10-01")
        val id = repo.create(monthly("2026-10-05"))
        time.set("2026-11-12")
        repo.ensureGenerated()
        val nov = rows(id).first { it.dueDate == d("2026-11-05") }
        repo.confirm(nov.id, brl("2500.00"), d("2026-11-05"), null)

        repo.updateTemplate(id, monthly("2026-10-05", amount = "2600.00"), nextDueDate = null)

        val all = rows(id)
        val oct = all.first { it.dueDate == d("2026-10-05") }
        assertEquals(OccurrenceState.PENDING, oct.state)
        assertEquals(brl("2500.00"), oct.expected)
        assertEquals(brl("2500.00"), all.first { it.dueDate == d("2026-11-05") }.actual)
        assertEquals(brl("2600.00"), all.first { it.dueDate == d("2026-12-05") }.expected)
        assertTrue(all.filter { it.dueDate >= d("2026-12-05") }.all { it.expected == brl("2600.00") })
        assertEquals(all.size, all.map { it.scheduledDate }.toSet().size)
    }

    @Test
    fun structuralEdit_replacesCustomized_andNeverDuplicatesAMonth() = runBlocking {
        val id = repo.create(monthly("2026-11-05"))
        val dec = rows(id).first { it.dueDate == d("2026-12-05") }
        repo.editOccurrence(dec.id, "Rent", brl("2500.00"), d("2026-12-07"))

        repo.updateTemplate(id, monthly("2026-11-05", amount = "2700.00"), nextDueDate = null)
        assertEquals(d("2026-12-07"), rows(id).first { it.scheduledDate == d("2026-12-05") }.dueDate)
        assertEquals(1, repo.customizedReplaceableCount(id))

        repo.updateTemplate(id, monthly("2026-11-05", amount = "2700.00"), nextDueDate = d("2026-12-10"))
        val december = rows(id).filter { it.dueDate.monthValue == 12 && it.dueDate.year == 2026 }
        assertEquals(listOf(d("2026-12-10")), december.map { it.dueDate })
        // The overdue 05/11 occurrence stays; the new rule starts on 10/12.
        assertEquals(listOf("2026-11-05", "2026-12-10", "2027-01-10"), dates(id).take(3))
    }

    @Test
    fun amountEdit_keepsMonthEndAnchor() = runBlocking {
        time.set("2026-01-15")
        val id = repo.create(monthly("2026-01-31"))
        time.set("2026-02-10")
        repo.updateTemplate(id, monthly("2026-01-31", amount = "2600.00"), nextDueDate = null)
        val upcoming = rows(id).filter { it.dueDate >= d("2026-02-10") }.map { it.dueDate.toString() }
        assertEquals(listOf("2026-02-28", "2026-03-31", "2026-04-30"), upcoming.take(3))
    }

    @Test
    fun endDateMovedEarlier_removesEveryPendingAfterIt() = runBlocking {
        val id = repo.create(monthly("2026-11-05"))
        val apr = rows(id).first { it.dueDate == d("2027-04-05") }
        repo.editOccurrence(apr.id, "Rent", brl("1.00"), d("2027-04-06"))
        repo.updateTemplate(id, monthly("2026-11-05").copy(endDate = d("2027-03-31")), nextDueDate = null)
        assertEquals("2027-03-05", dates(id).last())
        assertTrue(rows(id).none { it.scheduledDate!! > d("2027-03-31") })
    }

    @Test
    fun deactivate_thenReactivate_withoutBackfill() = runBlocking {
        val id = repo.create(monthly("2026-11-12"))
        repo.setTemplateActive(id, false)
        assertTrue(rows(id).isEmpty())
        time.set("2027-01-10")
        repo.setTemplateActive(id, true)
        assertEquals("2027-01-12", dates(id).first())
    }

    @Test
    fun deleteTemplate_keepsHistoryAsStandalone() = runBlocking {
        time.set("2026-10-01")
        val id = repo.create(monthly("2026-10-05"))
        time.set("2026-11-12")
        val nov = rows(id).first { it.dueDate == d("2026-11-05") }
        repo.confirm(nov.id, brl("2500.00"), d("2026-11-05"), null)
        repo.deleteTemplate(id)
        val left = repo.snapshot().occurrences
        assertEquals(listOf("2026-10-05", "2026-11-05"), left.map { it.dueDate.toString() }.sorted())
        assertTrue(left.all { it.templateId == null })
        assertNull(repo.template(id))
    }

    @Test
    fun confirm_keepsExpected_andUnconfirmClears() = runBlocking {
        val id = repo.create(monthly("2026-11-05"))
        val nov = rows(id).first()
        repo.confirm(nov.id, brl("2550.00"), d("2026-11-10"), "late fee")
        val paid = repo.occurrence(nov.id)!!
        assertEquals(brl("2500.00"), paid.expected)
        assertEquals(brl("2550.00"), paid.actual)
        assertEquals(brl("2500.00"), rows(id)[1].expected)

        repo.unconfirm(nov.id)
        val back = repo.occurrence(nov.id)!!
        assertEquals(OccurrenceState.PENDING, back.state)
        assertNull(back.actual)
        assertNull(back.note)
    }

    @Test(expected = IllegalArgumentException::class)
    fun confirm_inTheFuture_isRejected() = runBlocking {
        val id = repo.create(monthly("2026-11-05"))
        repo.confirm(rows(id).first().id, brl("1.00"), d("2026-11-13"), null)
    }

    @Test
    fun oneTime_alreadySettled_isConfirmed_andDeletable() = runBlocking {
        val id = repo.create(TransactionDraft("Groceries", rent, EntryKind.EXPENSE, brl("312.40"), d("2026-11-12"), null, alreadySettled = true))
        val row = repo.occurrence(id)!!
        assertEquals(OccurrenceState.CONFIRMED, row.state)
        assertEquals(brl("312.40"), row.actual)
        assertEquals(d("2026-11-12"), row.actualDate)
        repo.deleteOccurrence(id)
        assertNull(repo.occurrence(id))
    }

    @Test(expected = IllegalStateException::class)
    fun deletingGeneratedOccurrence_isRefused() = runBlocking {
        val id = repo.create(monthly("2026-11-05"))
        repo.deleteOccurrence(rows(id).first().id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun categoryKindMismatch_isRejected() = runBlocking<Unit> {
        repo.create(TransactionDraft("Salary", salary, EntryKind.EXPENSE, brl("1.00"), d("2026-11-12"), null))
    }

    @Test
    fun opening_isReplaced_andAdjustmentStoresTheDifference() = runBlocking {
        repo.setOpening(brl("9000.00"), d("2026-11-01"))
        repo.setOpening(brl("10000.00"), d("2026-11-01"))
        val openings = repo.snapshot().adjustments.filter { it.kind == AdjustmentKind.OPENING }
        assertEquals(1, openings.size)

        val delta = repo.addAdjustment(brl("9000.00"), d("2026-11-12"), "bank")
        assertEquals(brl("-1000.00"), delta)
        assertEquals(brl("9000.00"), CashCalculator.balance(Currency.BRL, d("2026-11-12"), repo.snapshot()))
        assertNull(repo.addAdjustment(brl("9000.00"), d("2026-11-12"), null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun adjustment_beforeOpening_isRejected() = runBlocking<Unit> {
        repo.setOpening(brl("10000.00"), d("2026-11-01"))
        repo.addAdjustment(brl("1.00"), d("2026-10-31"), null)
    }

    @Test(expected = OpeningAfterAdjustmentException::class)
    fun movingOpeningAfterAnAdjustment_isRefused() = runBlocking {
        repo.setOpening(brl("10000.00"), d("2026-11-01"))
        repo.addAdjustment(brl("9000.00"), d("2026-11-05"), null)
        repo.setOpening(brl("10000.00"), d("2026-11-06"))
    }

    @Test
    fun everyWrite_notifiesBackup() = runBlocking {
        val before = changes
        val id = repo.create(monthly("2026-11-05"))
        repo.confirm(rows(id).first().id, brl("1.00"), d("2026-11-05"), null)
        repo.setOpening(brl("1.00"), d("2026-11-01"))
        assertEquals(before + 3, changes)
    }
}
