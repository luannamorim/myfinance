package io.github.luannamorim.myfinance.data.repository

import androidx.room.withTransaction
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.db.MyFinanceDatabase
import io.github.luannamorim.myfinance.data.db.entity.AssetEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetFlowEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetValuationEntity
import io.github.luannamorim.myfinance.data.db.entity.OccurrenceEntity
import io.github.luannamorim.myfinance.data.images.ImageStore
import io.github.luannamorim.myfinance.domain.model.Asset
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
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
    private val cashDao = db.cashDao()

    /**
     * The whole portfolio, re-read as one snapshot (a single transaction) whenever any of its tables changes.
     * Separate per-table streams would update one at a time: right after a deletion the assets could arrive
     * without the deleted one while its flows were still there, which crashed every screen observing this.
     */
    val data: Flow<PortfolioData> = db.invalidationTracker
        .createFlow("asset", "asset_flow", "asset_valuation", "asset_category")
        .conflate()
        .map { snapshot() }

    private suspend fun snapshot(): PortfolioData = db.withTransaction {
        portfolioData(dao.listAssets(), dao.listFlows(), dao.listValuations(), db.categoryDao().listAssetCategories(includeArchived = true))
    }

    suspend fun asset(id: Long): Asset? = dao.getAsset(id)?.toDomain()

    /**
     * Creates the asset with its INITIAL flow on the acquisition date ([initial] may be 0 for inherited or
     * granted assets) and, when [initialValue] differs from it, a first valuation dated today. With [fromCash]
     * (the amount that left cash, in the cash currency) the initial investment is also a transfer out of cash.
     */
    suspend fun createAsset(draft: AssetDraft, initial: Money, initialValue: Money?, fromCash: Money? = null): Long {
        validate(draft)
        require(initial.currency == draft.currency && (initialValue == null || initialValue.currency == draft.currency)) { "Currency mismatch" }
        require(initial.minor >= 0 && (initialValue == null || initialValue.minor >= 0)) { "Amounts can't be negative" }
        val now = time.nowMs()
        val today = time.today()
        if (fromCash != null) {
            require(initial.minor > 0 && fromCash.minor > 0) { "A transfer needs an amount" }
            require(fromCash.currency != initial.currency || fromCash == initial) { "Same currency, same amount" }
            require(!draft.acquisitionDate.isAfter(today)) { "A transfer can't be in the future" }
        }
        val id = db.withTransaction {
            val id = dao.insertAsset(
                AssetEntity(
                    name = draft.name.trim(), categoryId = draft.categoryId, currency = draft.currency, description = draft.description.clean(),
                    institution = draft.institution.clean(), acquisitionDate = draft.acquisitionDate.toEpochDay(),
                    quantity = draft.quantity?.stripTrailingZeros()?.toPlainString(), notes = draft.notes.clean(), imagePath = draft.imagePath,
                    createdAtMs = now, updatedAtMs = now,
                ),
            )
            val transfer = fromCash?.let { insertTransfer(EntryKind.TRANSFER_OUT, id, draft.name.trim(), it, draft.acquisitionDate, null, now) }
            dao.insertFlow(
                AssetFlowEntity(
                    assetId = id, kind = FlowKind.INITIAL, date = draft.acquisitionDate.toEpochDay(), amount = initial.minor,
                    createdAtMs = now, occurrenceId = transfer,
                ),
            )
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
     * Not allowed after the position was closed or in the future. With [cash] (in the cash currency) the money
     * comes from cash (a contribution) or goes to it (a withdrawal or disposal): a confirmed one-time transfer
     * is recorded with the flow. Closing the position stops its recurring transfers.
     */
    suspend fun addFlow(
        assetId: Long,
        kind: FlowKind,
        date: LocalDate,
        amount: Money,
        quantityDelta: BigDecimal? = null,
        note: String? = null,
        newValue: Money? = null,
        cash: Money? = null,
    ) {
        require(kind != FlowKind.INITIAL) { "The initial flow is created with the asset" }
        require(amount.minor > 0) { "Amount must be positive" }
        require(cash == null || cash.minor > 0) { "Cash amount must be positive" }
        require(cash == null || cash.currency != amount.currency || cash == amount) { "Same currency, same amount" }
        val today = time.today()
        require(!date.isAfter(today)) { "Date can't be in the future" }
        db.withTransaction {
            val asset = checkNotNull(dao.getAsset(assetId)) { "Asset $assetId not found" }
            require(asset.currency == amount.currency) { "Flows are in the asset currency" }
            val flows = dao.getFlows(assetId)
            if (flows.any { it.kind == FlowKind.FULL_DISPOSAL }) throw AssetClosedException()
            val now = time.nowMs()
            val transfer = cash?.let {
                val direction = if (kind.inflow) EntryKind.TRANSFER_OUT else EntryKind.TRANSFER_IN
                insertTransfer(direction, assetId, asset.name, it, date, note, now)
            }
            dao.insertFlow(
                AssetFlowEntity(
                    assetId = assetId, kind = kind, date = date.toEpochDay(), amount = amount.minor,
                    quantityDelta = quantityDelta?.stripTrailingZeros()?.toPlainString(), note = note.clean(), createdAtMs = now,
                    occurrenceId = transfer,
                ),
            )
            if (newValue != null && kind != FlowKind.FULL_DISPOSAL) upsertValuation(assetId, date, newValue, null)
            if (kind == FlowKind.FULL_DISPOSAL) stopRecurringTransfers(assetId, today, delete = false)
        }
        backup.onDataChanged()
    }

    /** A confirmed one-time transfer between cash and [assetId]; returns its id (the caller links the flow). */
    private suspend fun insertTransfer(kind: EntryKind, assetId: Long, name: String, amount: Money, date: LocalDate, note: String?, now: Long): Long =
        cashDao.insertOccurrence(
            OccurrenceEntity(
                templateId = null, scheduledDate = null, dueDate = date.toEpochDay(), name = name, categoryId = null, kind = kind,
                currency = amount.currency, expectedAmount = amount.minor, state = OccurrenceState.CONFIRMED, actualAmount = amount.minor,
                actualCurrency = amount.currency, actualDate = date.toEpochDay(), note = note.clean(), confirmedAtMs = now,
                createdAtMs = now, updatedAtMs = now, assetId = assetId,
            ),
        )

    /**
     * Recurring transfers of an asset that is closed ([delete] false: deactivated, future pending ones removed)
     * or deleted ([delete] true: the templates and every pending transfer go; done ones stay in the cash history).
     */
    private suspend fun stopRecurringTransfers(assetId: Long, today: LocalDate, delete: Boolean) {
        cashDao.getTemplatesForAsset(assetId).forEach { t ->
            cashDao.deleteReplaceable(t.id, today.toEpochDay(), includeCustomized = delete)
            if (delete) cashDao.deleteTemplate(t.id) else if (t.active) cashDao.updateTemplate(t.copy(active = false, updatedAtMs = time.nowMs()))
        }
        if (delete) cashDao.deletePendingForAsset(assetId)
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

    /**
     * Deletes a flow; the INITIAL flow stays (it defines the asset). Deleting the closing reopens the position.
     * A flow that came from a transfer takes the transfer with it: a one-time transfer is deleted, a recurring
     * one goes back to pending.
     */
    suspend fun deleteFlow(id: Long) {
        db.withTransaction {
            val flow = checkNotNull(dao.getFlow(id)) { "Flow $id not found" }
            check(flow.kind != FlowKind.INITIAL) { "The initial flow can't be deleted" }
            dao.deleteFlow(id)
            val transfer = flow.occurrenceId?.let { cashDao.getOccurrence(it) } ?: return@withTransaction
            if (transfer.templateId == null) {
                cashDao.deleteOccurrence(transfer.id)
            } else {
                cashDao.updateOccurrence(
                    transfer.copy(
                        state = OccurrenceState.PENDING, actualAmount = null, actualCurrency = null, actualDate = null, note = null,
                        confirmedAtMs = null, updatedAtMs = time.nowMs(),
                    ),
                )
            }
        }
        backup.onDataChanged()
    }

    suspend fun deleteValuation(id: Long) {
        dao.deleteValuation(id)
        backup.onDataChanged()
    }

    /**
     * Deletes the asset with its flows, valuations and photo, and its planned transfers (recurring ones and
     * pending ones). Transfers already done stay in the cash history, without the asset.
     */
    suspend fun deleteAsset(id: Long) {
        val asset = dao.getAsset(id) ?: return
        db.withTransaction {
            stopRecurringTransfers(id, time.today(), delete = true)
            dao.deleteAsset(id)
        }
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
