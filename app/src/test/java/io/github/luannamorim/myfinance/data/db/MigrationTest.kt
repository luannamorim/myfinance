package io.github.luannamorim.myfinance.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Builds a version-1 database from the exported schema (app/schemas/…/1.json), fills it, then opens it with
 * Room at the current version: Room runs the migrations and validates every table against the entities.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @After fun cleanUp() {
        context.deleteDatabase(name)
    }

    private fun createVersion(version: Int, fill: (SupportSQLiteDatabase) -> Unit) {
        val schema = Json.parseToJsonElement(
            File("schemas/io.github.luannamorim.myfinance.data.db.MyFinanceDatabase/$version.json").readText(),
        ).jsonObject.getValue("database").jsonObject
        val statements = buildList {
            schema.getValue("entities").jsonArray.forEach { entity ->
                val e = entity.jsonObject
                val table = e.getValue("tableName").jsonPrimitive.content
                add(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                e["indices"]?.jsonArray?.forEach { add(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            schema.getValue("setupQueries").jsonArray.forEach { add(it.jsonPrimitive.content) }
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = statements.forEach(db::execSQL)
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        fill(helper.writableDatabase)
        helper.close()
    }

    @Test
    fun v1_to_v2_keepsEveryRow_andLinksStartEmpty() = runBlocking<Unit> {
        createVersion(1) { db ->
            db.execSQL("INSERT INTO category (id, name, kind, icon, seedKey, archived, position, createdAtMs) VALUES (1, NULL, 'EXPENSE', 'key', 'expense.rent', 0, 0, 0)")
            db.execSQL(
                "INSERT INTO transaction_template (id, name, categoryId, kind, currency, expectedAmount, recurrenceUnit, recurrenceInterval, " +
                    "startDate, anchorDate, endDate, description, active, generatedThrough, createdAtMs, updatedAtMs) " +
                    "VALUES (7, 'Rent', 1, 'EXPENSE', 'BRL', 250000, 'MONTH', 1, 20400, 20400, NULL, 'Apt 2', 1, 20800, 0, 0)",
            )
            db.execSQL(
                "INSERT INTO transaction_occurrence (id, templateId, scheduledDate, dueDate, name, categoryId, kind, currency, expectedAmount, " +
                    "description, state, actualAmount, actualCurrency, actualDate, note, confirmedAtMs, customized, createdAtMs, updatedAtMs) " +
                    "VALUES (11, 7, 20400, 20400, 'Rent', 1, 'EXPENSE', 'BRL', 250000, NULL, 'CONFIRMED', 255000, 'BRL', 20405, 'late', 5, 0, 0, 0)",
            )
            db.execSQL(
                "INSERT INTO transaction_occurrence (id, templateId, scheduledDate, dueDate, name, categoryId, kind, currency, expectedAmount, " +
                    "description, state, actualAmount, actualCurrency, actualDate, note, confirmedAtMs, customized, createdAtMs, updatedAtMs) " +
                    "VALUES (12, 7, 20431, 20431, 'Rent', 1, 'EXPENSE', 'BRL', 250000, NULL, 'PENDING', NULL, NULL, NULL, NULL, NULL, 1, 0, 0)",
            )
            db.execSQL("INSERT INTO asset_category (id, name, assetGroup, icon, seedKey, archived, position, createdAtMs) VALUES (3, NULL, 'INVESTMENT', 'receipt_long', 'asset.fixed_income', 0, 0, 0)")
            db.execSQL(
                "INSERT INTO asset (id, name, categoryId, currency, description, institution, acquisitionDate, quantity, notes, imagePath, createdAtMs, updatedAtMs) " +
                    "VALUES (5, 'Tesouro Selic', 3, 'BRL', NULL, NULL, 20000, NULL, NULL, NULL, 0, 0)",
            )
            db.execSQL("INSERT INTO asset_flow (id, assetId, kind, date, amount, quantityDelta, note, createdAtMs) VALUES (21, 5, 'INITIAL', 20000, 1000000, NULL, NULL, 0)")
            db.execSQL("INSERT INTO asset_flow (id, assetId, kind, date, amount, quantityDelta, note, createdAtMs) VALUES (22, 5, 'CONTRIBUTION', 20100, 50000, '1.5', 'extra', 0)")
            db.execSQL("INSERT INTO asset_valuation (id, assetId, date, value, note, createdAtMs) VALUES (31, 5, 20300, 1100000, NULL, 0)")
        }

        val room = Room.databaseBuilder(context, MyFinanceDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val cash = room.cashDao()
            val template = cash.getAllTemplates().single()
            assertEquals(1L, template.categoryId)
            assertNull(template.assetId)
            assertEquals("Apt 2", template.description)

            val occurrences = cash.getAllOccurrences().sortedBy { it.id }
            assertEquals(listOf(11L, 12L), occurrences.map { it.id })
            // The parent table was rebuilt without cascading: the link to the template is intact.
            assertTrue(occurrences.all { it.templateId == 7L && it.categoryId == 1L && it.assetId == null && it.kind == EntryKind.EXPENSE })
            assertEquals(OccurrenceState.CONFIRMED, occurrences[0].state)
            assertEquals(255000L, occurrences[0].actualAmount)
            assertTrue(occurrences[1].customized)

            val flows = room.portfolioDao().getFlows(5).sortedBy { it.id }
            assertEquals(listOf(21L, 22L), flows.map { it.id })
            assertTrue(flows.all { it.occurrenceId == null })
            assertEquals("1.5", flows[1].quantityDelta)
            assertEquals(1, room.portfolioDao().getValuations(5).size)

            // The category added in 1.1.0 is there for databases created before it.
            val loans = room.categoryDao().getAllAssetCategories().single { it.seedKey == "asset.loans_receivable" }
            assertEquals("PERSONAL", loans.assetGroup.name)
            val expenses = room.categoryDao().getAll().filter { it.kind == EntryKind.EXPENSE }.associateBy { it.seedKey }
            assertTrue(expenses.keys.containsAll(listOf("expense.rent", "expense.credit_card", "expense.loans")))

            room.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally {
            room.close()
        }
    }
}
