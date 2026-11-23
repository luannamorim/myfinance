package io.github.luannamorim.myfinance.data.backup

import androidx.room.withTransaction
import io.github.luannamorim.myfinance.data.db.MyFinanceDatabase
import io.github.luannamorim.myfinance.data.db.entity.AssetCategoryEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetFlowEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetPriceEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetSplitEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetValuationEntity
import io.github.luannamorim.myfinance.data.db.entity.CashAdjustmentEntity
import io.github.luannamorim.myfinance.data.db.entity.CategoryEntity
import io.github.luannamorim.myfinance.data.db.entity.ExchangeRateEntity
import io.github.luannamorim.myfinance.data.db.entity.OccurrenceEntity
import io.github.luannamorim.myfinance.data.db.entity.TransactionTemplateEntity
import io.github.luannamorim.myfinance.domain.backup.AdjustmentDto
import io.github.luannamorim.myfinance.domain.backup.AssetCategoryDto
import io.github.luannamorim.myfinance.domain.backup.AssetDto
import io.github.luannamorim.myfinance.domain.backup.AssetFlowDto
import io.github.luannamorim.myfinance.domain.backup.AssetPriceDto
import io.github.luannamorim.myfinance.domain.backup.AssetSplitDto
import io.github.luannamorim.myfinance.domain.backup.BackupDataV1
import io.github.luannamorim.myfinance.domain.backup.BackupError
import io.github.luannamorim.myfinance.domain.backup.CategoryDto
import io.github.luannamorim.myfinance.domain.backup.ExchangeRateDto
import io.github.luannamorim.myfinance.domain.backup.OccurrenceDto
import io.github.luannamorim.myfinance.domain.backup.TemplateDto
import io.github.luannamorim.myfinance.domain.backup.ValuationDto
import io.github.luannamorim.myfinance.domain.market.Ticker
import java.math.BigDecimal

/** Converts the whole database to/from the versioned backup DTOs. */
object SnapshotMapper {

    /** Rows ready to insert, already validated (enum names, decimal strings). */
    class Rows internal constructor(
        val categories: List<CategoryEntity>,
        val templates: List<TransactionTemplateEntity>,
        val occurrences: List<OccurrenceEntity>,
        val adjustments: List<CashAdjustmentEntity>,
        val assetCategories: List<AssetCategoryEntity>,
        val assets: List<AssetEntity>,
        val assetFlows: List<AssetFlowEntity>,
        val valuations: List<AssetValuationEntity>,
        val exchangeRates: List<ExchangeRateEntity>,
        val assetPrices: List<AssetPriceEntity>,
        val assetSplits: List<AssetSplitEntity>,
    )

    suspend fun export(db: MyFinanceDatabase): BackupDataV1 = db.withTransaction {
        BackupDataV1(
            categories = db.categoryDao().getAll().map { it.toDto() },
            templates = db.cashDao().getAllTemplates().map { it.toDto() },
            occurrences = db.cashDao().getAllOccurrences().map { it.toDto() },
            adjustments = db.cashDao().getAllAdjustments().map { it.toDto() },
            assetCategories = db.categoryDao().getAllAssetCategories().map { it.toDto() },
            assets = db.portfolioDao().getAllAssets().map { it.toDto() },
            assetFlows = db.portfolioDao().getAllFlows().map { it.toDto() },
            valuations = db.portfolioDao().getAllValuations().map { it.toDto() },
            exchangeRates = db.rateDao().getAll().map { it.toDto() },
            assetPrices = db.marketDao().listPrices().map { it.toDto() },
            assetSplits = db.marketDao().listSplits().map { it.toDto() },
        )
    }

    /** Converts and validates [data]. Throws [BackupError.Corrupted] for unknown enum values or invalid decimals. */
    fun toRows(data: BackupDataV1): Rows = Rows(
        categories = data.categories.map { it.toEntity() },
        templates = data.templates.map { it.toEntity() },
        occurrences = data.occurrences.map { it.toEntity() },
        adjustments = data.adjustments.map { it.toEntity() },
        assetCategories = data.assetCategories.map { it.toEntity() },
        assets = data.assets.map { it.toEntity() },
        assetFlows = data.assetFlows.map { it.toEntity() },
        valuations = data.valuations.map { it.toEntity() },
        exchangeRates = data.exchangeRates.map { it.toEntity() },
        assetPrices = data.assetPrices.map { it.toEntity() },
        assetSplits = data.assetSplits.map { it.toEntity() },
    )

    /** Replaces every table with [data] in a single transaction, preserving ids. */
    suspend fun replaceAll(db: MyFinanceDatabase, data: BackupDataV1) = replaceAll(db, toRows(data))

    suspend fun replaceAll(db: MyFinanceDatabase, rows: Rows) {
        val dao = db.backupDao()
        db.withTransaction {
            // Children before parents: flows point at occurrences and assets, occurrences at templates and assets.
            dao.clearAssetFlows()
            dao.clearValuations()
            dao.clearOccurrences()
            dao.clearTemplates()
            dao.clearCategories()
            dao.clearAdjustments()
            dao.clearAssets()
            dao.clearAssetCategories()
            dao.clearExchangeRates()
            dao.clearAssetPrices()
            dao.clearAssetSplits()

            // Parents before children.
            dao.insertCategories(rows.categories)
            dao.insertAssetCategories(rows.assetCategories)
            dao.insertAssets(rows.assets)
            dao.insertTemplates(rows.templates)
            dao.insertOccurrences(rows.occurrences)
            dao.insertAdjustments(rows.adjustments)
            dao.insertAssetFlows(rows.assetFlows)
            dao.insertValuations(rows.valuations)
            dao.insertExchangeRates(rows.exchangeRates)
            dao.insertAssetPrices(rows.assetPrices)
            dao.insertAssetSplits(rows.assetSplits)
        }
    }

    private fun CategoryEntity.toDto() = CategoryDto(id, name, kind.name, icon, seedKey, archived, position, createdAtMs)
    private fun CategoryDto.toEntity() = CategoryEntity(id, name, enumOf(kind), icon, seedKey, archived, position, createdAtMs)

    private fun TransactionTemplateEntity.toDto() = TemplateDto(
        id, name, categoryId, kind.name, currency.name, expectedAmount, recurrenceUnit.name, recurrenceInterval,
        startDate, anchorDate, endDate, description, active, generatedThrough, createdAtMs, updatedAtMs, assetId,
    )
    private fun TemplateDto.toEntity() = TransactionTemplateEntity(
        id, name, categoryId, enumOf(kind), enumOf(currency), expectedAmount, enumOf(recurrenceUnit), recurrenceInterval,
        startDate, anchorDate, endDate, description, active, generatedThrough, createdAtMs, updatedAtMs, assetId,
    )

    private fun OccurrenceEntity.toDto() = OccurrenceDto(
        id, templateId, scheduledDate, dueDate, name, categoryId, kind.name, currency.name, expectedAmount, description,
        state.name, actualAmount, actualCurrency?.name, actualDate, note, confirmedAtMs, customized, createdAtMs, updatedAtMs, assetId,
    )
    private fun OccurrenceDto.toEntity() = OccurrenceEntity(
        id, templateId, scheduledDate, dueDate, name, categoryId, enumOf(kind), enumOf(currency), expectedAmount, description,
        enumOf(state), actualAmount, actualCurrency?.let { enumOf(it) }, actualDate, note, confirmedAtMs, customized, createdAtMs, updatedAtMs, assetId,
    )

    private fun CashAdjustmentEntity.toDto() = AdjustmentDto(id, kind.name, currency.name, date, amount, note, createdAtMs)
    private fun AdjustmentDto.toEntity() = CashAdjustmentEntity(id, enumOf(kind), enumOf(currency), date, amount, note, createdAtMs)

    private fun AssetCategoryEntity.toDto() = AssetCategoryDto(id, name, assetGroup.name, icon, seedKey, archived, position, createdAtMs)
    private fun AssetCategoryDto.toEntity() = AssetCategoryEntity(id, name, enumOf(assetGroup), icon, seedKey, archived, position, createdAtMs)

    private fun AssetEntity.toDto() = AssetDto(
        id, name, categoryId, currency.name, description, institution, acquisitionDate, quantity, notes, imagePath, createdAtMs, updatedAtMs, ticker,
    )
    private fun AssetDto.toEntity() = AssetEntity(
        id, name, categoryId, enumOf(currency), description, institution, acquisitionDate, decimal(quantity), notes, imagePath, createdAtMs, updatedAtMs, ticker(ticker),
    )

    private fun AssetFlowEntity.toDto() = AssetFlowDto(id, assetId, kind.name, date, amount, quantityDelta, note, createdAtMs, occurrenceId)
    private fun AssetFlowDto.toEntity() = AssetFlowEntity(id, assetId, enumOf(kind), date, amount, decimal(quantityDelta), note, createdAtMs, occurrenceId)

    private fun AssetValuationEntity.toDto() = ValuationDto(id, assetId, date, value, note, createdAtMs)
    private fun ValuationDto.toEntity() = AssetValuationEntity(id, assetId, date, value, note, createdAtMs)

    private fun ExchangeRateEntity.toDto() = ExchangeRateDto(id, base.name, quote.name, date, rateMicros, source.name, fetchedAtMs)
    private fun ExchangeRateDto.toEntity() = ExchangeRateEntity(id, enumOf(base), enumOf(quote), date, rateMicros, enumOf(source), fetchedAtMs)

    // Ids are left out of the file: Room assigns new ones on restore.
    private fun AssetPriceEntity.toDto() = AssetPriceDto(symbol, date, price, source.name, fetchedAtMs)
    private fun AssetPriceDto.toEntity() = AssetPriceEntity(
        symbol = symbol(symbol), date = date, price = positiveDecimal(price), source = enumOf(source), fetchedAtMs = fetchedAtMs,
    )

    private fun AssetSplitEntity.toDto() = AssetSplitDto(symbol, date, numerator, denominator)
    private fun AssetSplitDto.toEntity() = AssetSplitEntity(
        symbol = symbol(symbol), date = date, numerator = positiveDecimal(numerator), denominator = positiveDecimal(denominator),
    )

    /** Unknown values (e.g. a newer app's enum) reject the backup instead of storing rows the app can't read. */
    private inline fun <reified E : Enum<E>> enumOf(name: String): E =
        enumValues<E>().firstOrNull { it.name == name } ?: throw BackupError.Corrupted("unknown ${E::class.simpleName}")

    /** A tracked symbol must be one the app can track, already in its normalized form. */
    private fun ticker(text: String?): String? {
        if (text != null && Ticker.normalize(text) != text) throw BackupError.Corrupted("invalid ticker")
        return text
    }

    private fun symbol(text: String): String = Ticker.normalize(text)?.takeIf { it == text } ?: throw BackupError.Corrupted("invalid symbol")

    private fun positiveDecimal(text: String): String {
        val value = runCatching { BigDecimal(text) }.getOrNull()
        if (value == null || value.signum() <= 0) throw BackupError.Corrupted("invalid decimal")
        return text
    }

    /** Quantities are parsed as BigDecimal when shown; reject anything that isn't a plain decimal. */
    private fun decimal(text: String?): String? {
        if (text != null && runCatching { BigDecimal(text) }.isFailure) throw BackupError.Corrupted("invalid decimal")
        return text
    }
}
