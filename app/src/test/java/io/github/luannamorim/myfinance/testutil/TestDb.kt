package io.github.luannamorim.myfinance.testutil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.luannamorim.myfinance.data.db.MyFinanceDatabase
import io.github.luannamorim.myfinance.data.db.entity.AssetEntity
import io.github.luannamorim.myfinance.data.db.entity.CategoryEntity
import io.github.luannamorim.myfinance.data.db.entity.OccurrenceEntity
import io.github.luannamorim.myfinance.data.db.entity.TransactionTemplateEntity
import io.github.luannamorim.myfinance.data.db.seed.CategorySeed
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.RecurrenceUnit
import java.time.LocalDate

fun inMemoryDb(seed: Boolean = false): MyFinanceDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return Room.inMemoryDatabaseBuilder(context, MyFinanceDatabase::class.java)
        .apply { if (seed) addCallback(CategorySeed.Callback { 0L }) }
        .allowMainThreadQueries()
        .build()
}

fun day(text: String): Long = LocalDate.parse(text).toEpochDay()

suspend fun MyFinanceDatabase.insertCategory(kind: EntryKind = EntryKind.EXPENSE, name: String = "Test"): Long =
    categoryDao().insert(CategoryEntity(name = name, kind = kind, icon = "category", position = 0, createdAtMs = 0))

suspend fun MyFinanceDatabase.insertTemplate(categoryId: Long, anchor: String = "2026-11-05", amount: Long = 250_000): Long =
    cashDao().insertTemplate(
        TransactionTemplateEntity(
            name = "Rent", categoryId = categoryId, kind = EntryKind.EXPENSE, currency = Currency.BRL,
            expectedAmount = amount, recurrenceUnit = RecurrenceUnit.MONTH, recurrenceInterval = 1,
            startDate = day(anchor), anchorDate = day(anchor), createdAtMs = 0, updatedAtMs = 0,
        ),
    )

fun occurrence(categoryId: Long, templateId: Long?, scheduled: String?, due: String = scheduled ?: "2026-11-12") =
    OccurrenceEntity(
        templateId = templateId, scheduledDate = scheduled?.let(::day), dueDate = day(due), name = "Rent",
        categoryId = categoryId, kind = EntryKind.EXPENSE, currency = Currency.BRL, expectedAmount = 250_000,
        createdAtMs = 0, updatedAtMs = 0,
    )

fun asset(categoryId: Long, name: String = "Tesouro Selic", currency: Currency = Currency.BRL) =
    AssetEntity(name = name, categoryId = categoryId, currency = currency, acquisitionDate = day("2026-01-10"), createdAtMs = 0, updatedAtMs = 0)
