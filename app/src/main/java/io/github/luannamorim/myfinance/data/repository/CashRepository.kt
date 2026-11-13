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
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What the transaction editor saves. [alreadySettled] applies to one-time transactions only. */
data class TransactionDraft(
    val name: String,
    val categoryId: Long,
    val kind: EntryKind,
    val amount: Money,
    val date: LocalDate,
    val rule: RecurrenceRule?,
    val endDate: LocalDate? = null,
    val description: String? = null,
    val alreadySettled: Boolean = false,
)

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
    suspend fun create(draft: TransactionDraft): Long {
        validate(draft)
        val now = time.nowMs()
        val today = time.today()
        val id = generation.withLock {
            db.withTransaction {
                if (draft.rule == null) {
                    val settled = draft.alreadySettled && !draft.date.isAfter(today)
                    dao.insertOccurrence(
                        OccurrenceEntity(
                            templateId = null, scheduledDate = null, dueDate = draft.date.toEpochDay(), name = draft.name.trim(),
                            categoryId = draft.categoryId, kind = draft.kind, currency = draft.amount.currency,
                            expectedAmount = draft.amount.minor, description = draft.description.clean(),
                            state = if (settled) OccurrenceState.CONFIRMED else OccurrenceState.PENDING,
                            actualAmount = if (settled) draft.amount.minor else null,
                            actualCurrency = if (settled) draft.amount.currency else null,
                            actualDate = if (settled) draft.date.toEpochDay() else null,
                            confirmedAtMs = if (settled) now else null,
                            createdAtMs = now, updatedAtMs = now,
                        ),
                    )
                } else {
                    // Past occurrences are not backfilled: generation starts this month at the earliest.
                    val start = maxOf(draft.date, today.withDayOfMonth(1))
                    val templateId = dao.insertTemplate(
                        TransactionTemplateEntity(
                            name = draft.name.trim(), categoryId = draft.categoryId, kind = draft.kind, currency = draft.amount.currency,
                            expectedAmount = draft.amount.minor, recurrenceUnit = draft.rule.unit, recurrenceInterval = draft.rule.interval,
                            startDate = draft.date.toEpochDay(), anchorDate = draft.date.toEpochDay(), endDate = draft.endDate?.toEpochDay(),
                            description = draft.description.clean(), generatedThrough = start.minusDays(1).toEpochDay(),
                            createdAtMs = now, updatedAtMs = now,
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
    suspend fun updateTemplate(id: Long, draft: TransactionDraft, nextDueDate: LocalDate?) {
        val rule = requireNotNull(draft.rule) { "A recurring template needs a rule" }
        validate(draft)
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
                        updatedAtMs = time.nowMs(),
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
                    description = t.description, createdAtMs = now, updatedAtMs = now,
                )
            },
        )
        dao.updateTemplate(t.copy(generatedThrough = maxOf(horizon.toEpochDay(), t.generatedThrough ?: Long.MIN_VALUE)))
        return ids.count { it > 0 }
    }

    /** Confirms a payment or receipt. The expected amount and every other occurrence stay as they are. */
    suspend fun confirm(id: Long, actual: Money, date: LocalDate, note: String?) {
        require(!date.isAfter(time.today())) { "Confirmation date can't be in the future" }
        require(actual.minor > 0) { "Amount must be positive" }
        update(id) {
            check(it.state == OccurrenceState.PENDING) { "Only pending occurrences can be confirmed" }
            it.copy(
                state = OccurrenceState.CONFIRMED, actualAmount = actual.minor, actualCurrency = actual.currency,
                actualDate = date.toEpochDay(), note = note.clean(), confirmedAtMs = time.nowMs(),
            )
        }
    }

    /** "Mark as unpaid": clears the actual values. */
    suspend fun unconfirm(id: Long) = update(id) {
        check(it.state == OccurrenceState.CONFIRMED) { "Only confirmed occurrences can be undone" }
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
    suspend fun updateStandalone(id: Long, draft: TransactionDraft) {
        require(draft.rule == null) { "Standalone transactions have no rule" }
        validate(draft)
        update(id) {
            check(it.templateId == null) { "Generated occurrences are edited with editOccurrence" }
            val settle = draft.alreadySettled && it.state == OccurrenceState.PENDING && !draft.date.isAfter(time.today())
            it.copy(
                name = draft.name.trim(), categoryId = draft.categoryId, kind = draft.kind, currency = draft.amount.currency,
                expectedAmount = draft.amount.minor, dueDate = draft.date.toEpochDay(), description = draft.description.clean(),
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

    private suspend fun update(id: Long, change: (OccurrenceEntity) -> OccurrenceEntity) {
        db.withTransaction {
            val row = checkNotNull(dao.getOccurrence(id)) { "Occurrence $id not found" }
            dao.updateOccurrence(change(row).copy(updatedAtMs = time.nowMs()))
        }
        backup.onDataChanged()
    }

    private suspend fun validate(draft: TransactionDraft) {
        require(draft.name.isNotBlank()) { "Name is required" }
        require(draft.amount.minor > 0) { "Amount must be positive" }
        val category = checkNotNull(db.categoryDao().get(draft.categoryId)) { "Category not found" }
        require(category.kind == draft.kind) { "Category kind doesn't match the transaction" }
        require(draft.endDate == null || !draft.endDate.isBefore(draft.date)) { "End date is before the first date" }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
