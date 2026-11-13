package io.github.luannamorim.myfinance.data.repository

import androidx.room.withTransaction
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.db.MyFinanceDatabase
import io.github.luannamorim.myfinance.data.db.entity.CashAdjustmentEntity
import io.github.luannamorim.myfinance.data.db.entity.OccurrenceEntity
import io.github.luannamorim.myfinance.data.db.entity.TransactionTemplateEntity
import io.github.luannamorim.myfinance.domain.cash.CashCalculator
import io.github.luannamorim.myfinance.domain.cash.CashLedger
import io.github.luannamorim.myfinance.domain.cash.Recurrence
import io.github.luannamorim.myfinance.domain.cash.RecurrenceRule
import io.github.luannamorim.myfinance.domain.model.AdjustmentKind
import io.github.luannamorim.myfinance.domain.model.AssetFlow
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the transaction editor saves. Income and expenses have a [categoryId]; transfers have an [assetId]
 * instead, and a blank [name] (it defaults to the asset's). [alreadySettled] applies to one-time transactions
 * only; a settled transfer between currencies also needs [assetAmount], in the asset's currency.
 */
data class TransactionDraft(
    val name: String,
    val categoryId: Long?,
    val kind: EntryKind,
    val amount: Money,
    val date: LocalDate,
    val rule: RecurrenceRule?,
    val endDate: LocalDate? = null,
    val description: String? = null,
    val alreadySettled: Boolean = false,
    val assetId: Long? = null,
    val assetAmount: Money? = null,
)

/** A confirmed transfer keeps its asset and direction: undo it first. */
class TransferLockedException : IllegalStateException("A confirmed transfer can't change its asset or direction")

/** The opening balance can't move after an existing adjustment (the adjustment would silently stop counting). */
class OpeningAfterAdjustmentException : IllegalStateException("Opening balance date is after an existing adjustment")

/**
 * Templates, occurrences and cash adjustments. Generation rules live in docs/ARCHITECTURE.md ("Recurrence"):
 * idempotent generation guarded by the unique (templateId, scheduledDate) index, and template edits that only
 * replace pending occurrences due from today on.
 */
@Singleton
class CashRepository @Inject constructor(
    private val db: MyFinanceDatabase,
    private val time: TimeSource,
    private val backup: BackupTrigger,
) {
    private val dao = db.cashDao()
    private val transfers = TransferFlows(db)
    private val generation = Mutex()

    val ledger: Flow<CashLedger> = combine(dao.observeTemplates(), dao.observeOccurrences(), dao.observeAdjustments()) { t, o, a ->
        CashLedger(t.map { it.toDomain() }, o.map { it.toDomain() }, a.map { it.toDomain() })
    }

    suspend fun snapshot(): CashLedger = db.withTransaction {
        CashLedger(
            dao.getAllTemplates().map { it.toDomain() },
            dao.getAllOccurrences().map { it.toDomain() },
            dao.getAllAdjustments().map { it.toDomain() },
        )
    }

    suspend fun template(id: Long) = dao.getTemplate(id)?.toDomain()

    suspend fun occurrence(id: Long) = dao.getOccurrence(id)?.toDomain()

    /** Creates a one-time transaction (an occurrence) or a recurring one (a template plus its occurrences). */
    suspend fun create(input: TransactionDraft): Long {
        val draft = validate(input, currentAssetId = null)
        val now = time.nowMs()
        val today = time.today()
        val id = generation.withLock {
            db.withTransaction {
                if (draft.rule == null) {
                    val settled = draft.alreadySettled && !draft.date.isAfter(today)
                    val row = OccurrenceEntity(
                        templateId = null, scheduledDate = null, dueDate = draft.date.toEpochDay(), name = draft.name.trim(),
                        categoryId = draft.categoryId, kind = draft.kind, currency = draft.amount.currency,
                        expectedAmount = draft.amount.minor, description = draft.description.clean(),
                        state = if (settled) OccurrenceState.CONFIRMED else OccurrenceState.PENDING,
                        actualAmount = if (settled) draft.amount.minor else null,
                        actualCurrency = if (settled) draft.amount.currency else null,
                        actualDate = if (settled) draft.date.toEpochDay() else null,
                        confirmedAtMs = if (settled) now else null,
                        createdAtMs = now, updatedAtMs = now, assetId = draft.assetId,
                    )
                    val id = dao.insertOccurrence(row)
                    transfers.sync(row.copy(id = id), draft.assetAmount, now)
                    id
                } else {
                    // Past occurrences are not backfilled: generation starts this month at the earliest.
                    val start = maxOf(draft.date, today.withDayOfMonth(1))
                    val templateId = dao.insertTemplate(
                        TransactionTemplateEntity(
                            name = draft.name.trim(), categoryId = draft.categoryId, kind = draft.kind, currency = draft.amount.currency,
                            expectedAmount = draft.amount.minor, recurrenceUnit = draft.rule.unit, recurrenceInterval = draft.rule.interval,
                            startDate = draft.date.toEpochDay(), anchorDate = draft.date.toEpochDay(), endDate = draft.endDate?.toEpochDay(),
                            description = draft.description.clean(), generatedThrough = start.minusDays(1).toEpochDay(),
                            createdAtMs = now, updatedAtMs = now, assetId = draft.assetId,
                        ),
                    )
                    generate(templateId, today)
                    templateId
                }
            }
        }
        backup.onDataChanged()
        return id
    }

    /**
     * Applies an edit to a recurring template. [nextDueDate] is passed only when the user changed it (it becomes
     * the new anchor and must be today or later). A rule or next-due change is structural and also replaces
     * individually edited pending occurrences; any other edit keeps them.
     */
    suspend fun updateTemplate(id: Long, input: TransactionDraft, nextDueDate: LocalDate?) {
        val rule = requireNotNull(input.rule) { "A recurring template needs a rule" }
        val draft = validate(input, currentAssetId = dao.getTemplate(id)?.assetId)
        val today = time.today()
        require(nextDueDate == null || !nextDueDate.isBefore(today)) { "Next due date must be today or later" }
        generation.withLock {
            db.withTransaction {
                val current = checkNotNull(dao.getTemplate(id)) { "Template $id not found" }
                val structural = nextDueDate != null ||
                    rule != RecurrenceRule(current.recurrenceUnit, current.recurrenceInterval)
                dao.deleteReplaceable(id, today.toEpochDay(), includeCustomized = structural)
                dao.updateTemplate(
                    current.copy(
                        name = draft.name.trim(), categoryId = draft.categoryId, kind = draft.kind, currency = draft.amount.currency,
                        expectedAmount = draft.amount.minor, recurrenceUnit = rule.unit, recurrenceInterval = rule.interval,
                        anchorDate = nextDueDate?.toEpochDay() ?: current.anchorDate, endDate = draft.endDate?.toEpochDay(),
                        description = draft.description.clean(), generatedThrough = today.minusDays(1).toEpochDay(),
                        updatedAtMs = time.nowMs(), assetId = draft.assetId,
                    ),
                )
                if (current.active) generate(id, today)
                draft.endDate?.let { dao.deletePendingAfter(id, it.toEpochDay()) }
            }
        }
        backup.onDataChanged()
    }

    /** How many individually edited pending occurrences a structural edit would replace. */
    suspend fun customizedReplaceableCount(templateId: Long): Int =
        dao.countCustomizedReplaceable(templateId, time.today().toEpochDay())

    suspend fun setTemplateActive(id: Long, active: Boolean) {
        val today = time.today()
        generation.withLock {
            db.withTransaction {
                val current = checkNotNull(dao.getTemplate(id)) { "Template $id not found" }
                if (current.active == active) return@withTransaction
                if (active) {
                    check(!current.kind.isTransfer || current.assetId != null) { "The asset of this transfer was deleted" }
                    dao.updateTemplate(current.copy(active = true, generatedThrough = today.minusDays(1).toEpochDay(), updatedAtMs = time.nowMs()))
                    generate(id, today)
                } else {
                    dao.deleteReplaceable(id, today.toEpochDay(), includeCustomized = false)
                    dao.updateTemplate(current.copy(active = false, updatedAtMs = time.nowMs()))
                }
            }
        }
        backup.onDataChanged()
    }

    /** Deletes the template and its future pending occurrences; the rest stay as standalone history. */
    suspend fun deleteTemplate(id: Long) {
        generation.withLock {
            db.withTransaction {
                dao.deleteReplaceable(id, time.today().toEpochDay(), includeCustomized = false)
                dao.deleteTemplate(id)
            }
        }
        backup.onDataChanged()
    }

    /** Tops up every active template to the horizon. Safe to call any number of times. */
    suspend fun ensureGenerated() {
        val today = time.today()
        val inserted = generation.withLock {
            db.withTransaction { dao.getActiveTemplates().sumOf { generate(it.id, today) } }
        }
        if (inserted > 0) backup.onDataChanged()
    }

    /** Must run inside a transaction holding [generation]. Returns how many occurrences were inserted. */
    private suspend fun generate(templateId: Long, today: LocalDate): Int {
        val t = dao.getTemplate(templateId) ?: return 0
        if (!t.active) return 0
        val horizon = Recurrence.horizonEnd(today)
        val anchor = t.anchorDate.toDate()
        val from = maxOf(anchor, (t.generatedThrough?.toDate() ?: anchor.minusDays(1)).plusDays(1))
        val rule = RecurrenceRule(t.recurrenceUnit, t.recurrenceInterval)
        val dates = Recurrence.datesBetween(anchor, rule, from, horizon, t.endDate?.toDate())
        val now = time.nowMs()
        val ids = dao.insertOccurrencesIgnore(
            dates.map { date ->
                OccurrenceEntity(
                    templateId = t.id, scheduledDate = date.toEpochDay(), dueDate = date.toEpochDay(), name = t.name,
                    categoryId = t.categoryId, kind = t.kind, currency = t.currency, expectedAmount = t.expectedAmount,
                    description = t.description, createdAtMs = now, updatedAtMs = now, assetId = t.assetId,
                )
            },
        )
        dao.updateTemplate(t.copy(generatedThrough = maxOf(horizon.toEpochDay(), t.generatedThrough ?: Long.MIN_VALUE)))
        return ids.count { it > 0 }
    }

    /**
     * Confirms a payment, receipt or transfer. The expected amount and every other occurrence stay as they are.
     * A transfer also records its contribution or withdrawal on the asset; [assetAmount] is what the asset
     * received or gave, required when [actual] is in another currency.
     */
    suspend fun confirm(id: Long, actual: Money, date: LocalDate, note: String?, assetAmount: Money? = null) {
        require(!date.isAfter(time.today())) { "Confirmation date can't be in the future" }
        require(actual.minor > 0) { "Amount must be positive" }
        update(id, assetAmount) {
            check(it.state == OccurrenceState.PENDING) { "Only pending occurrences can be confirmed" }
            it.copy(
                state = OccurrenceState.CONFIRMED, actualAmount = actual.minor, actualCurrency = actual.currency,
                actualDate = date.toEpochDay(), note = note.clean(), confirmedAtMs = time.nowMs(),
            )
        }
    }

    /** "Mark as unpaid": clears the actual values (and removes a transfer's contribution or withdrawal). */
    suspend fun unconfirm(id: Long) = update(id) {
        check(it.state == OccurrenceState.CONFIRMED) { "Only confirmed occurrences can be undone" }
        transfers.checkNotInitial(id)
        it.copy(state = OccurrenceState.PENDING, actualAmount = null, actualCurrency = null, actualDate = null, note = null, confirmedAtMs = null)
    }

    suspend fun cancel(id: Long) = update(id) {
        check(it.state == OccurrenceState.PENDING) { "Only pending occurrences can be cancelled" }
        it.copy(state = OccurrenceState.CANCELLED)
    }

    suspend fun restore(id: Long) = update(id) {
        check(it.state == OccurrenceState.CANCELLED) { "Only cancelled occurrences can be restored" }
        it.copy(state = OccurrenceState.PENDING)
    }

    /** Edits one generated occurrence; it keeps its values through later template edits. */
    suspend fun editOccurrence(id: Long, name: String, amount: Money, dueDate: LocalDate) {
        require(name.isNotBlank()) { "Name is required" }
        require(amount.minor > 0) { "Amount must be positive" }
        update(id) {
            check(it.state == OccurrenceState.PENDING) { "Only pending occurrences can be edited" }
            it.copy(name = name.trim(), expectedAmount = amount.minor, currency = amount.currency, dueDate = dueDate.toEpochDay(), customized = true)
        }
    }

    /** Edits a standalone (one-time) transaction. */
    suspend fun updateStandalone(id: Long, input: TransactionDraft) {
        require(input.rule == null) { "Standalone transactions have no rule" }
        val draft = validate(input, currentAssetId = dao.getOccurrence(id)?.assetId)
        update(id, draft.assetAmount) {
            check(it.templateId == null) { "Generated occurrences are edited with editOccurrence" }
            val transfer = it.kind.isTransfer || draft.kind.isTransfer
            if (it.state == OccurrenceState.CONFIRMED && transfer && (it.kind != draft.kind || it.assetId != draft.assetId)) {
                throw TransferLockedException()
            }
            val settle = draft.alreadySettled && it.state == OccurrenceState.PENDING && !draft.date.isAfter(time.today())
            it.copy(
                name = draft.name.trim(), categoryId = draft.categoryId, kind = draft.kind, currency = draft.amount.currency,
                expectedAmount = draft.amount.minor, dueDate = draft.date.toEpochDay(), description = draft.description.clean(),
                assetId = draft.assetId,
            ).let { row ->
                if (!settle) row else row.copy(
                    state = OccurrenceState.CONFIRMED, actualAmount = draft.amount.minor, actualCurrency = draft.amount.currency,
                    actualDate = draft.date.toEpochDay(), confirmedAtMs = time.nowMs(),
                )
            }
        }
    }

    /** Deletes a standalone occurrence. Generated ones are cancelled instead. */
    suspend fun deleteOccurrence(id: Long) {
        db.withTransaction {
            val row = checkNotNull(dao.getOccurrence(id)) { "Occurrence $id not found" }
            check(row.templateId == null) { "Generated occurrences can't be deleted; cancel them instead" }
            transfers.detach(id)
            dao.deleteOccurrence(id)
        }
        backup.onDataChanged()
    }

    /** Sets (or replaces) the opening balance of [amount]'s currency. */
    suspend fun setOpening(amount: Money, date: LocalDate) {
        db.withTransaction {
            val firstAdjustment = dao.firstAdjustmentDate(amount.currency)
            if (firstAdjustment != null && firstAdjustment < date.toEpochDay()) throw OpeningAfterAdjustmentException()
            val existing = dao.getOpening(amount.currency)
            if (existing == null) {
                dao.insertAdjustment(
                    CashAdjustmentEntity(kind = AdjustmentKind.OPENING, currency = amount.currency, date = date.toEpochDay(), amount = amount.minor, createdAtMs = time.nowMs()),
                )
            } else {
                dao.updateAdjustment(existing.copy(date = date.toEpochDay(), amount = amount.minor))
            }
        }
        backup.onDataChanged()
    }

    /**
     * Records the difference between the real balance [target] and the computed balance on [date]
     * (neither income nor expense). Returns the stored difference, or null when there was none.
     */
    suspend fun addAdjustment(target: Money, date: LocalDate, note: String?): Money? {
        require(!date.isAfter(time.today())) { "Adjustment date can't be in the future" }
        val delta = db.withTransaction {
            val opening = dao.getOpening(target.currency)
            require(opening == null || date.toEpochDay() >= opening.date) { "Adjustment date is before the opening balance" }
            val ledger = CashLedger(
                occurrences = dao.getAllOccurrences().map { it.toDomain() },
                adjustments = dao.getAllAdjustments().map { it.toDomain() },
            )
            val delta = target - CashCalculator.balance(target.currency, date, ledger)
            if (delta.isZero) return@withTransaction null
            dao.insertAdjustment(
                CashAdjustmentEntity(
                    kind = AdjustmentKind.ADJUSTMENT, currency = target.currency, date = date.toEpochDay(),
                    amount = delta.minor, note = note.clean(), createdAtMs = time.nowMs(),
                ),
            )
            delta
        }
        if (delta != null) backup.onDataChanged()
        return delta
    }

    suspend fun deleteAdjustment(id: Long) {
        dao.deleteAdjustment(id)
        backup.onDataChanged()
    }

    /** Changes one occurrence and keeps a transfer's asset flow in step, in one transaction. */
    private suspend fun update(id: Long, assetAmount: Money? = null, change: suspend (OccurrenceEntity) -> OccurrenceEntity) {
        db.withTransaction {
            val row = checkNotNull(dao.getOccurrence(id)) { "Occurrence $id not found" }
            val now = time.nowMs()
            val updated = change(row).copy(updatedAtMs = now)
            dao.updateOccurrence(updated)
            transfers.sync(updated, assetAmount, now)
        }
        backup.onDataChanged()
    }

    /** The contribution or withdrawal a confirmed transfer recorded on its asset. */
    suspend fun transferFlow(occurrenceId: Long): AssetFlow? {
        val portfolio = db.portfolioDao()
        val flow = portfolio.getFlowByOccurrence(occurrenceId) ?: return null
        val asset = portfolio.getAsset(flow.assetId) ?: return null
        return flow.toDomain(asset.currency)
    }

    /**
     * Checks [draft] and returns it ready to store: a transfer's blank name becomes its asset's. A transfer
     * can't be planned to a closed position, unless it already pointed at it ([currentAssetId]).
     */
    private suspend fun validate(draft: TransactionDraft, currentAssetId: Long?): TransactionDraft {
        require(draft.amount.minor > 0) { "Amount must be positive" }
        require(draft.endDate == null || !draft.endDate.isBefore(draft.date)) { "End date is before the first date" }
        if (draft.kind.isTransfer) {
            require(draft.categoryId == null) { "Transfers have no category" }
            val assetId = requireNotNull(draft.assetId) { "A transfer needs an asset" }
            val portfolio = db.portfolioDao()
            val asset = checkNotNull(portfolio.getAsset(assetId)) { "Asset $assetId not found" }
            if (assetId != currentAssetId && portfolio.getFlows(assetId).any { it.kind == FlowKind.FULL_DISPOSAL }) throw AssetClosedException()
            return draft.copy(name = draft.name.trim().ifEmpty { asset.name })
        }
        require(draft.name.isNotBlank()) { "Name is required" }
        require(draft.assetId == null) { "Only transfers have an asset" }
        val category = checkNotNull(db.categoryDao().get(requireNotNull(draft.categoryId) { "Category is required" })) { "Category not found" }
        require(category.kind == draft.kind) { "Category kind doesn't match the transaction" }
        return draft
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
