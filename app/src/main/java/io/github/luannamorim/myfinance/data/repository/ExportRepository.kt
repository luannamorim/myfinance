package io.github.luannamorim.myfinance.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.luannamorim.myfinance.data.db.MyFinanceDatabase
import io.github.luannamorim.myfinance.data.db.seed.CategorySeed
import io.github.luannamorim.myfinance.di.IoDispatcher
import io.github.luannamorim.myfinance.domain.export.Csv
import io.github.luannamorim.myfinance.domain.model.Money
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports everything to a zip of CSV files for spreadsheets (docs/BACKUP.md, "CSV export"). The export is not
 * encrypted; the screen warns before writing it. Restoring the app always uses the encrypted backup.
 */
@Singleton
class ExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: MyFinanceDatabase,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun exportTo(uri: Uri) = withContext(io) {
        val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("Cannot open the destination")
        out.use { export(it) }
    }

    suspend fun export(out: OutputStream) = withContext(io) {
        val categories = db.categoryDao().getAll()
        val categoryName = categories.associate { it.id to (it.name ?: it.seedKey?.let(CategorySeed::defaultName).orEmpty()) }
        val assetCategories = db.categoryDao().getAllAssetCategories()
        val assetCategory = assetCategories.associate { it.id to (it.name ?: it.seedKey?.let(CategorySeed::defaultName).orEmpty()) }
        val assetGroup = assetCategories.associate { it.id to it.assetGroup.name }
        val cash = db.cashDao()
        val portfolio = db.portfolioDao()
        val assets = portfolio.getAllAssets()
        val assetById = assets.associateBy { it.id }

        val zip = ZipOutputStream(out)
        fun file(name: String, header: List<String>, rows: List<List<String?>>) {
            zip.putNextEntry(ZipEntry(name))
            val text = StringBuilder(Csv.row(header))
            rows.forEach { text.append(Csv.row(it)) }
            zip.write(text.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        fun day(epochDay: Long?) = Csv.date(epochDay?.let(LocalDate::ofEpochDay))

        file(
            "transactions.csv",
            listOf("id", "type", "name", "category", "asset", "status", "due_date", "expected_amount", "currency", "actual_date", "actual_amount", "actual_currency", "recurring", "description", "note"),
            cash.getAllOccurrences().sortedWith(compareBy({ it.dueDate }, { it.id })).map { o ->
                listOf(
                    o.id.toString(), o.kind.name, Csv.safeText(o.name), Csv.safeText(o.categoryId?.let(categoryName::get)),
                    Csv.safeText(o.assetId?.let { assetById[it]?.name }), o.state.name, day(o.dueDate),
                    Csv.amount(Money(o.expectedAmount, o.currency)), o.currency.name, day(o.actualDate),
                    o.actualAmount?.let { Csv.amount(Money(it, o.actualCurrency ?: o.currency)) }, (o.actualCurrency ?: o.currency.takeIf { o.actualAmount != null })?.name,
                    if (o.templateId != null) "yes" else "no", Csv.safeText(o.description), Csv.safeText(o.note),
                )
            },
        )
        file(
            "cash_adjustments.csv",
            listOf("kind", "date", "amount", "currency", "note"),
            cash.getAllAdjustments().sortedBy { it.date }.map { a ->
                listOf(a.kind.name, day(a.date), Csv.amount(Money(a.amount, a.currency)), a.currency.name, Csv.safeText(a.note))
            },
        )
        file(
            "assets.csv",
            listOf("id", "name", "category", "group", "currency", "institution", "acquisition_date", "quantity", "description", "notes"),
            assets.map { a ->
                listOf(
                    a.id.toString(), Csv.safeText(a.name), Csv.safeText(assetCategory[a.categoryId]), assetGroup[a.categoryId], a.currency.name,
                    Csv.safeText(a.institution), day(a.acquisitionDate), a.quantity, Csv.safeText(a.description), Csv.safeText(a.notes),
                )
            },
        )
        file(
            "asset_flows.csv",
            listOf("asset_id", "asset", "kind", "direction", "date", "amount", "currency", "quantity_delta", "note", "transfer_id"),
            portfolio.getAllFlows().sortedWith(compareBy({ it.date }, { it.id })).mapNotNull { f ->
                val asset = assetById[f.assetId] ?: return@mapNotNull null
                listOf(
                    f.assetId.toString(), Csv.safeText(asset.name), f.kind.name, if (f.kind.inflow) "in" else "out", day(f.date),
                    Csv.amount(Money(f.amount, asset.currency)), asset.currency.name, f.quantityDelta, Csv.safeText(f.note),
                    f.occurrenceId?.toString(),
                )
            },
        )
        file(
            "valuations.csv",
            listOf("asset_id", "asset", "date", "value", "currency", "note"),
            portfolio.getAllValuations().sortedWith(compareBy({ it.date }, { it.id })).mapNotNull { v ->
                val asset = assetById[v.assetId] ?: return@mapNotNull null
                listOf(v.assetId.toString(), Csv.safeText(asset.name), day(v.date), Csv.amount(Money(v.value, asset.currency)), asset.currency.name, Csv.safeText(v.note))
            },
        )
        file(
            "exchange_rates.csv",
            listOf("date", "base", "quote", "rate", "source"),
            db.rateDao().getAll().sortedWith(compareBy({ it.date }, { it.source })).map { r ->
                listOf(day(r.date), r.base.name, r.quote.name, BigDecimal.valueOf(r.rateMicros, 6).stripTrailingZeros().toPlainString(), r.source.name)
            },
        )
        zip.finish()
    }

    companion object {
        /** Files in the export, in order. */
        val FILES = listOf("transactions.csv", "cash_adjustments.csv", "assets.csv", "asset_flows.csv", "valuations.csv", "exchange_rates.csv")
    }
}
