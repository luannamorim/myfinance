package io.github.luannamorim.myfinance.domain.model

import io.github.luannamorim.myfinance.domain.cash.RecurrenceRule
import java.math.BigDecimal
import java.time.LocalDate

/** Income or expense category. [name] is null for a predefined category never renamed (shown localized by [seedKey]). */
data class Category(
    val id: Long,
    val name: String?,
    val kind: EntryKind,
    val icon: String,
    val seedKey: String?,
    val archived: Boolean,
    val position: Int,
)

/** A recurring transaction definition. Occurrences are generated from [anchorDate] by [rule]. */
data class TransactionTemplate(
    val id: Long,
    val name: String,
    val categoryId: Long,
    val kind: EntryKind,
    val expected: Money,
    val rule: RecurrenceRule,
    val startDate: LocalDate,
    val anchorDate: LocalDate,
    val endDate: LocalDate?,
    val description: String?,
    val active: Boolean,
    val generatedThrough: LocalDate?,
)

/**
 * One dated income or expense: generated from a template ([templateId] set) or standalone (one-time, or kept
 * after its template was deleted). Expected and actual values are kept apart.
 */
data class Occurrence(
    val id: Long,
    val templateId: Long?,
    val scheduledDate: LocalDate?,
    val dueDate: LocalDate,
    val name: String,
    val categoryId: Long,
    val kind: EntryKind,
    val expected: Money,
    val description: String?,
    val state: OccurrenceState,
    val actual: Money?,
    val actualDate: LocalDate?,
    val note: String?,
    val customized: Boolean,
) {
    val standalone: Boolean get() = templateId == null
}

/** Opening balance or balance adjustment of one currency's cash. [amount] is signed. */
data class CashAdjustment(
    val id: Long,
    val kind: AdjustmentKind,
    val date: LocalDate,
    val amount: Money,
    val note: String?,
)

data class AssetCategory(
    val id: Long,
    val name: String?,
    val group: AssetGroup,
    val icon: String,
    val seedKey: String?,
    val archived: Boolean,
    val position: Int,
)

/** An investment or personal asset. Its flows and valuations are in [currency], which never changes. */
data class Asset(
    val id: Long,
    val name: String,
    val categoryId: Long,
    val currency: Currency,
    val description: String?,
    val institution: String?,
    val acquisitionDate: LocalDate,
    val quantity: BigDecimal?,
    val notes: String?,
    val imagePath: String?,
)

data class AssetFlow(
    val id: Long,
    val assetId: Long,
    val kind: FlowKind,
    val date: LocalDate,
    val amount: Money,
    val quantityDelta: BigDecimal?,
    val note: String?,
)

data class Valuation(
    val id: Long,
    val assetId: Long,
    val date: LocalDate,
    val value: Money,
    val note: String?,
)

/** USD→BRL rate on [date]. */
data class ExchangeRate(
    val date: LocalDate,
    val rate: BigDecimal,
    val source: RateSource,
)
