package io.github.luannamorim.myfinance.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.data.db.entity.AssetCategoryEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetFlowEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetValuationEntity
import io.github.luannamorim.myfinance.data.db.entity.ExchangeRateEntity
import io.github.luannamorim.myfinance.data.db.entity.OccurrenceEntity
import io.github.luannamorim.myfinance.data.db.seed.CategorySeed
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.RateSource
import io.github.luannamorim.myfinance.testutil.asset
import io.github.luannamorim.myfinance.testutil.day
import io.github.luannamorim.myfinance.testutil.inMemoryDb
import io.github.luannamorim.myfinance.testutil.insertCategory
import io.github.luannamorim.myfinance.testutil.insertTemplate
import io.github.luannamorim.myfinance.testutil.occurrence
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class DaoConstraintsTest {

    private val db = inMemoryDb(seed = true)

    @After
    fun close() = db.close()

    @Test
    fun seeds_insertPredefinedCategories() = runBlocking {
        val cash = db.categoryDao().getAll()
        val assets = db.categoryDao().getAllAssetCategories()
        assertEquals(6, cash.count { it.kind == EntryKind.INCOME })
        assertEquals(17, cash.count { it.kind == EntryKind.EXPENSE })
        assertEquals(7, assets.count { it.assetGroup == AssetGroup.INVESTMENT })
        assertEquals(6, assets.count { it.assetGroup == AssetGroup.PERSONAL })
        assertTrue(cash.all { it.name == null } && assets.all { it.name == null })
        val frozen = javaClass.getResource("/seed-keys.txt")!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(frozen, CategorySeed.allKeys)
        assertEquals(frozen.toSet(), (cash.mapNotNull { it.seedKey } + assets.mapNotNull { it.seedKey }).toSet())
    }

    @Test
    fun observe_hidesArchived() = runBlocking {
        val dao = db.categoryDao()
        val rent = dao.getAll().first { it.seedKey == "expense.rent" }
        dao.update(rent.copy(archived = true))
        assertTrue(dao.observe(EntryKind.EXPENSE, includeArchived = false).first().none { it.id == rent.id })
        assertTrue(dao.observe(EntryKind.EXPENSE, includeArchived = true).first().any { it.id == rent.id })
    }

    @Test(expected = SQLiteConstraintException::class)
    fun deletingUsedCategory_isRestricted() = runBlocking {
        val cat = db.insertCategory()
        db.cashDao().insertOccurrence(occurrence(cat, templateId = null, scheduled = null))
        assertEquals(1, db.categoryDao().usageCount(cat))
        db.categoryDao().delete(cat)
    }

    @Test
    fun deletingTemplate_keepsOccurrencesWithNullTemplate() = runBlocking {
        val cat = db.insertCategory()
        val template = db.insertTemplate(cat)
        val id = db.cashDao().insertOccurrence(occurrence(cat, template, "2026-11-05"))
        db.cashDao().deleteTemplate(template)
        assertNull(db.cashDao().getOccurrence(id)!!.templateId)
    }

    @Test
    fun uniqueTemplateDate_ignoresDuplicates_butStandaloneRowsCoexist() = runBlocking {
        val cat = db.insertCategory()
        val template = db.insertTemplate(cat)
        val dao = db.cashDao()
        val first = dao.insertOccurrencesIgnore(listOf(occurrence(cat, template, "2026-11-05"), occurrence(cat, template, "2026-12-05")))
        val again = dao.insertOccurrencesIgnore(listOf(occurrence(cat, template, "2026-11-05")))
        assertTrue(first.all { it > 0 })
        assertEquals(listOf(-1L), again)
        dao.insertOccurrence(occurrence(cat, templateId = null, scheduled = null))
        dao.insertOccurrence(occurrence(cat, templateId = null, scheduled = null))
        assertEquals(4, dao.getAllOccurrences().size)
    }

    @Test
    fun deleteReplaceable_keepsConfirmedPastAndCustomized() = runBlocking {
        val cat = db.insertCategory()
        val template = db.insertTemplate(cat, anchor = "2026-10-05")
        val dao = db.cashDao()
        dao.insertOccurrencesIgnore(
            listOf(
                occurrence(cat, template, "2026-10-05"), // overdue
                occurrence(cat, template, "2026-11-05").copy(state = io.github.luannamorim.myfinance.domain.model.OccurrenceState.CONFIRMED),
                occurrence(cat, template, "2026-12-05", due = "2026-12-07").copy(customized = true),
                occurrence(cat, template, "2027-01-05"),
            ),
        )
        assertEquals(1, dao.countCustomizedReplaceable(template, day("2026-11-12")))
        assertEquals(1, dao.deleteReplaceable(template, day("2026-11-12"), includeCustomized = false))
        assertEquals(listOf("2026-10-05", "2026-11-05", "2026-12-05"), dao.getOccurrences(template).map { java.time.LocalDate.ofEpochDay(it.scheduledDate!!).toString() })
        assertEquals(1, dao.deleteReplaceable(template, day("2026-11-12"), includeCustomized = true))
        assertEquals(1, dao.deletePendingAfter(template, day("2026-09-30")))
        assertEquals(1, dao.getOccurrences(template).size)
    }

    @Test
    fun deletingAsset_cascadesFlowsAndValuations() = runBlocking {
        val category = db.categoryDao().insertAssetCategory(AssetCategoryEntity(name = "X", assetGroup = AssetGroup.INVESTMENT, icon = "x", position = 0, createdAtMs = 0))
        val dao = db.portfolioDao()
        val id = dao.insertAsset(asset(category))
        dao.insertFlow(AssetFlowEntity(assetId = id, kind = FlowKind.INITIAL, date = day("2026-01-10"), amount = 500_000, createdAtMs = 0))
        dao.insertValuation(AssetValuationEntity(assetId = id, date = day("2026-03-31"), value = 512_000, createdAtMs = 0))
        dao.deleteAsset(id)
        assertTrue(dao.getAllFlows().isEmpty())
        assertTrue(dao.getAllValuations().isEmpty())
    }

    @Test(expected = SQLiteConstraintException::class)
    fun valuation_isUniquePerDay() = runBlocking<Unit> {
        val category = db.categoryDao().insertAssetCategory(AssetCategoryEntity(name = "X", assetGroup = AssetGroup.INVESTMENT, icon = "x", position = 0, createdAtMs = 0))
        val id = db.portfolioDao().insertAsset(asset(category))
        db.portfolioDao().insertValuation(AssetValuationEntity(assetId = id, date = day("2026-03-31"), value = 1, createdAtMs = 0))
        db.portfolioDao().insertValuation(AssetValuationEntity(assetId = id, date = day("2026-03-31"), value = 2, createdAtMs = 0))
    }

    @Test
    fun rates_bcbAndManualCoexist_andFetchedDuplicatesAreIgnored() = runBlocking {
        val dao = db.rateDao()
        fun rate(micros: Long, source: RateSource) =
            ExchangeRateEntity(base = Currency.USD, quote = Currency.BRL, date = day("2026-11-12"), rateMicros = micros, source = source, fetchedAtMs = 0)
        dao.insertIgnore(listOf(rate(5_614_300, RateSource.BCB)))
        dao.upsertManual(rate(5_700_000, RateSource.MANUAL))
        dao.insertIgnore(listOf(rate(9_999_999, RateSource.BCB)))
        dao.upsertManual(rate(5_650_000, RateSource.MANUAL))
        val all = dao.getAll()
        assertEquals(2, all.size)
        assertEquals(5_614_300L, all.first { it.source == RateSource.BCB }.rateMicros)
        assertEquals(5_650_000L, all.first { it.source == RateSource.MANUAL }.rateMicros)
        dao.deleteManual(day("2026-11-12"))
        assertEquals(listOf(RateSource.BCB), dao.getAll().map { it.source })
        assertEquals(day("2026-11-12"), dao.latestBcbDate())
    }

    @Test
    fun standaloneOccurrence_hasNoTemplate() {
        val o: OccurrenceEntity = occurrence(1, null, null)
        assertNull(o.templateId)
    }
}
