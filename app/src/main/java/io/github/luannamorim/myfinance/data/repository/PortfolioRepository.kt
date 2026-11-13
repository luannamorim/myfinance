package io.github.luannamorim.myfinance.data.repository

import androidx.room.withTransaction
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.db.MyFinanceDatabase
import io.github.luannamorim.myfinance.data.db.entity.AssetEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetFlowEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetValuationEntity
import io.github.luannamorim.myfinance.data.images.ImageStore
import io.github.luannamorim.myfinance.domain.model.Asset
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What the asset editor saves. The currency only applies when creating. */
data class AssetDraft(
    val name: String,
    val categoryId: Long,
    val currency: Currency,
    val description: String? = null,
    val institution: String? = null,
    val acquisitionDate: LocalDate,
    val quantity: BigDecimal? = null,
    val notes: String? = null,
    val imagePath: String? = null,
)

enum class ValuationResult { ADDED, REPLACED }

/** Adding a flow or valuation after the position was closed (delete the closing first). */
class AssetClosedException : IllegalStateException("The position is closed")

/**
 * Assets, their cash flows and valuations. Flows and valuations are in the asset's currency, which never
 * changes; valuations are a history (one per day; a same-day valuation replaces that day's).
 */
@Singleton
class PortfolioRepository @Inject constructor(
    private val db: MyFinanceDatabase,
    private val time: TimeSource,
    private val backup: BackupTrigger,
    private val images: ImageStore,
) {
    private val dao = db.portfolioDao()

    val data: Flow<PortfolioData> = combine(
        dao.observeAssets(),
        dao.observeFlows(),
        dao.observeValuations(),
        db.categoryDao().observeAssetCategories(includeArchived = true),
    ) { assets, flows, valuations, categories ->
        val domainAssets = assets.map { it.toDomain() }
        val currency = domainAssets.associate { it.id to it.currency }
        PortfolioData(
            assets = domainAssets,
            flows = flows.groupBy { it.assetId }.mapValues { (id, list) -> list.map { it.toDomain(currency.getValue(id)) } },
            valuations = valuations.groupBy { it.assetId }.mapValues { (id, list) -> list.map { it.toDomain(currency.getValue(id)) } },
            categories = categories.map { it.toDomain() },
        )
    }

    suspend fun asset(id: Long): Asset? = dao.getAsset(id)?.toDomain()

    /**
     * Creates the asset with its INITIAL flow on the acquisition date ([initial] may be 0 for inherited or
     * granted assets) and, when [initialValue] differs from it, a first valuation dated today.
     */
    suspend fun createAsset(draft: AssetDraft, initial: Money, initialValue: Money?): Long {
        validate(draft)
        require(initial.currency == draft.currency && (initialValue == null || initialValue.currency == draft.currency)) { "Currency mismatch" }
        require(initial.minor >= 0 && (initialValue == null || initialValue.minor >= 0)) { "Amounts can't be negative" }
        val now = time.nowMs()
        val today = time.today()
        val id = db.withTransaction {
            val id = dao.insertAsset(
                AssetEntity(
                    name = draft.name.trim(), categoryId = draft.categoryId, currency = draft.currency, description = draft.description.clean(),
                    institution = draft.institution.clean(), acquisitionDate = draft.acquisitionDate.toEpochDay(),
                    quantity = draft.quantity?.stripTrailingZeros()?.toPlainString(), notes = draft.notes.clean(), imagePath = draft.imagePath,
                    createdAtMs = now, updatedAtMs = now,
                ),
            )
            dao.insertFlow(AssetFlowEntity(assetId = id, kind = FlowKind.INITIAL, date = draft.acquisitionDate.toEpochDay(), amount = initial.minor, createdAtMs = now))
            if (initialValue != null && initialValue != initial) {
                val date = maxOf(today, draft.acquisitionDate)
                dao.insertValuation(AssetValuationEntity(assetId = id, date = date.toEpochDay(), value = initialValue.minor, createdAtMs = now))
            }
            id
        }
        backup.onDataChanged()
        return id
    }

    /** Updates the details. The currency can't change: flows and valuations are in it. */
    suspend fun updateAsset(id: Long, draft: AssetDraft) {
        validate(draft)
        val current = checkNotNull(dao.getAsset(id)) { "Asset $id not found" }
        require(current.currency == draft.currency) { "The currency of an asset can't change" }
        if (current.imagePath != null && current.imagePath != draft.imagePath) images.delete(current.imagePath)
        dao.updateAsset(
            current.copy(
                name = draft.name.trim(), categoryId = draft.categoryId, description = draft.description.clean(), institution = draft.institution.clean(),
                acquisitionDate = draft.acquisitionDate.toEpochDay(), quantity = draft.quantity?.stripTrailingZeros()?.toPlainString(),
                notes = draft.notes.clean(), imagePath = draft.imagePath, updatedAtMs = time.nowMs(),
            ),
        )
        backup.onDataChanged()
    }

    /**
     * Records a contribution, withdrawal or disposal; optionally the new estimated value on the same date.
     * Not allowed after the position was closed or in the future.
     */
    suspend fun addFlow(assetId: Long, kind: FlowKind, date: LocalDate, amount: Money, quantityDelta: BigDecimal? = null, note: String? = null, newValue: Money? = null) {
        require(kind != FlowKind.INITIAL) { "The initial flow is created with the asset" }
        require(amount.minor > 0) { "Amount must be positive" }
        require(!date.isAfter(time.today())) { "Date can't be in the future" }
        db.withTransaction {
            val asset = checkNotNull(dao.getAsset(assetId)) { "Asset $assetId not found" }
            require(asset.currency == amount.currency) { "Flows are in the asset currency" }
            val flows = dao.getFlows(assetId)
            if (flows.any { it.kind == FlowKind.FULL_DISPOSAL }) throw AssetClosedException()
            dao.insertFlow(
                AssetFlowEntity(
                    assetId = assetId, kind = kind, date = date.toEpochDay(), amount = amount.minor,
                    quantityDelta = quantityDelta?.stripTrailingZeros()?.toPlainString(), note = note.clean(), createdAtMs = time.nowMs(),
                ),
            )
            if (newValue != null && kind != FlowKind.FULL_DISPOSAL) upsertValuation(assetId, date, newValue, null)
        }
        backup.onDataChanged()
    }

    /** Adds a valuation; a second one on the same date replaces that day's. */
    suspend fun addValuation(assetId: Long, date: LocalDate, value: Money, note: String? = null): ValuationResult {
        require(value.minor >= 0) { "Value can't be negative" }
        require(!date.isAfter(time.today())) { "Date can't be in the future" }
        val result = db.withTransaction {
            val asset = checkNotNull(dao.getAsset(assetId)) { "Asset $assetId not found" }
            require(asset.currency == value.currency) { "Valuations are in the asset currency" }
            val closing = dao.getFlows(assetId).firstOrNull { it.kind == FlowKind.FULL_DISPOSAL }
            if (closing != null && date.toEpochDay() >= closing.date) throw AssetClosedException()
            upsertValuation(assetId, date, value, note)
        }
        backup.onDataChanged()
        return result
    }

    private suspend fun upsertValuation(assetId: Long, date: LocalDate, value: Money, note: String?): ValuationResult {
        val existing = dao.getValuationOn(assetId, date.toEpochDay())
        return if (existing != null) {
            dao.updateValuation(existing.copy(value = value.minor, note = note.clean(), createdAtMs = time.nowMs()))
            ValuationResult.REPLACED
        } else {
            dao.insertValuation(AssetValuationEntity(assetId = assetId, date = date.toEpochDay(), value = value.minor, note = note.clean(), createdAtMs = time.nowMs()))
            ValuationResult.ADDED
        }
    }

    suspend fun hasValuationOn(assetId: Long, date: LocalDate): Boolean = dao.getValuationOn(assetId, date.toEpochDay()) != null

    /** Deletes a flow; the INITIAL flow stays (it defines the asset). Deleting the closing reopens the position. */
    suspend fun deleteFlow(id: Long) {
        val flow = checkNotNull(dao.getFlow(id)) { "Flow $id not found" }
        check(flow.kind != FlowKind.INITIAL) { "The initial flow can't be deleted" }
        dao.deleteFlow(id)
        backup.onDataChanged()
    }

    suspend fun deleteValuation(id: Long) {
        dao.deleteValuation(id)
        backup.onDataChanged()
    }

    /** Deletes the asset with its flows, valuations and photo. */
    suspend fun deleteAsset(id: Long) {
        val asset = dao.getAsset(id) ?: return
        dao.deleteAsset(id)
        images.delete(asset.imagePath)
        backup.onDataChanged()
    }

    private suspend fun validate(draft: AssetDraft) {
        require(draft.name.isNotBlank()) { "Name is required" }
        require(draft.quantity == null || draft.quantity.signum() >= 0) { "Quantity can't be negative" }
        checkNotNull(db.categoryDao().getAssetCategory(draft.categoryId)) { "Category not found" }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
