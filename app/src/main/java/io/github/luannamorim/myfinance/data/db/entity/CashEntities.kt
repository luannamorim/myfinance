package io.github.luannamorim.myfinance.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.luannamorim.myfinance.domain.model.AdjustmentKind
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.domain.model.RecurrenceUnit

/** Income/expense category. `name` is NULL for a predefined category never renamed (localized by seedKey). */
@Entity(
    tableName = "category",
    indices = [Index(value = ["seedKey"], unique = true), Index("kind")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String?,
    val kind: EntryKind,
    val icon: String,
    val seedKey: String? = null,
    val archived: Boolean = false,
    val position: Int,
    val createdAtMs: Long,
)

/** Recurring transaction definition; dates are epoch days, amounts minor units. */
@Entity(
    tableName = "transaction_template",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("categoryId")],
)
data class TransactionTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long,
    val kind: EntryKind,
    val currency: Currency,
    val expectedAmount: Long,
    val recurrenceUnit: RecurrenceUnit,
    val recurrenceInterval: Int,
    val startDate: Long,
    val anchorDate: Long,
    val endDate: Long? = null,
    val description: String? = null,
    val active: Boolean = true,
    val generatedThrough: Long? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

/**
 * One dated income/expense. `scheduledDate` is the nominal date from the rule (part of the unique key, never
 * changes); `dueDate` can be moved for this occurrence alone. Name, kind, currency and description are snapshots.
 */
@Entity(
    tableName = "transaction_occurrence",
    foreignKeys = [
        ForeignKey(entity = TransactionTemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["templateId", "scheduledDate"], unique = true),
        Index("dueDate"),
        Index("actualDate"),
        Index("state"),
        Index("categoryId"),
    ],
)
data class OccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long?,
    val scheduledDate: Long?,
    val dueDate: Long,
    val name: String,
    val categoryId: Long,
    val kind: EntryKind,
    val currency: Currency,
    val expectedAmount: Long,
    val description: String? = null,
    val state: OccurrenceState = OccurrenceState.PENDING,
    val actualAmount: Long? = null,
    val actualCurrency: Currency? = null,
    val actualDate: Long? = null,
    val note: String? = null,
    val confirmedAtMs: Long? = null,
    val customized: Boolean = false,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

/** Opening balance (one per currency) or a balance adjustment; `amount` is signed. */
@Entity(tableName = "cash_adjustment", indices = [Index(value = ["currency", "kind"])])
data class CashAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: AdjustmentKind,
    val currency: Currency,
    val date: Long,
    val amount: Long,
    val note: String? = null,
    val createdAtMs: Long,
)
