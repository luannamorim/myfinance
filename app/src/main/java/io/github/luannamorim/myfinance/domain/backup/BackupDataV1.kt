package io.github.luannamorim.myfinance.domain.backup

import kotlinx.serialization.Serializable

/**
 * Backup data, format version 1: one flat list per table, independent of the Room schema. Field names are part
 * of the file format: never rename them; add new optional fields with defaults instead.
 *
 * Conventions: ids are kept; money is a `Long` in minor units (flows and valuations use their asset's currency);
 * currencies and enums are stored by name; calendar dates are epoch days; instants are epoch milliseconds;
 * exchange rates are `rateMicros` (rate × 10⁶); quantities are decimal strings. Predefined categories that were
 * never renamed keep a `null` name.
 */
@Serializable
data class BackupDataV1(
    val categories: List<CategoryDto> = emptyList(),
    val templates: List<TemplateDto> = emptyList(),
    val occurrences: List<OccurrenceDto> = emptyList(),
    val adjustments: List<AdjustmentDto> = emptyList(),
    val assetCategories: List<AssetCategoryDto> = emptyList(),
    val assets: List<AssetDto> = emptyList(),
    val assetFlows: List<AssetFlowDto> = emptyList(),
    val valuations: List<ValuationDto> = emptyList(),
    val exchangeRates: List<ExchangeRateDto> = emptyList(),
) {
    /** Rows per table, written to the manifest and checked on restore. */
    fun counts(): Map<String, Int> = mapOf(
        "categories" to categories.size,
        "templates" to templates.size,
        "occurrences" to occurrences.size,
        "adjustments" to adjustments.size,
        "assetCategories" to assetCategories.size,
        "assets" to assets.size,
        "assetFlows" to assetFlows.size,
        "valuations" to valuations.size,
        "exchangeRates" to exchangeRates.size,
    )

    /** Every image path referenced by the data (relative to filesDir). */
    fun referencedImages(): Set<String> = assets.mapNotNull { it.imagePath }.toSet()
}

@Serializable
data class CategoryDto(
    val id: Long, val name: String? = null, val kind: String, val icon: String, val seedKey: String? = null,
    val archived: Boolean = false, val position: Int, val createdAtMs: Long,
)

@Serializable
data class TemplateDto(
    val id: Long, val name: String, val categoryId: Long, val kind: String, val currency: String, val expectedAmount: Long,
    val recurrenceUnit: String, val recurrenceInterval: Int, val startDate: Long, val anchorDate: Long, val endDate: Long? = null,
    val description: String? = null, val active: Boolean = true, val generatedThrough: Long? = null,
    val createdAtMs: Long, val updatedAtMs: Long,
)

@Serializable
data class OccurrenceDto(
    val id: Long, val templateId: Long? = null, val scheduledDate: Long? = null, val dueDate: Long, val name: String,
    val categoryId: Long, val kind: String, val currency: String, val expectedAmount: Long, val description: String? = null,
    val state: String, val actualAmount: Long? = null, val actualCurrency: String? = null, val actualDate: Long? = null,
    val note: String? = null, val confirmedAtMs: Long? = null, val customized: Boolean = false,
    val createdAtMs: Long, val updatedAtMs: Long,
)

@Serializable
data class AdjustmentDto(
    val id: Long, val kind: String, val currency: String, val date: Long, val amount: Long, val note: String? = null, val createdAtMs: Long,
)

@Serializable
data class AssetCategoryDto(
    val id: Long, val name: String? = null, val assetGroup: String, val icon: String, val seedKey: String? = null,
    val archived: Boolean = false, val position: Int, val createdAtMs: Long,
)

@Serializable
data class AssetDto(
    val id: Long, val name: String, val categoryId: Long, val currency: String, val description: String? = null,
    val institution: String? = null, val acquisitionDate: Long, val quantity: String? = null, val notes: String? = null,
    val imagePath: String? = null, val createdAtMs: Long, val updatedAtMs: Long,
)

@Serializable
data class AssetFlowDto(
    val id: Long, val assetId: Long, val kind: String, val date: Long, val amount: Long, val quantityDelta: String? = null,
    val note: String? = null, val createdAtMs: Long,
)

@Serializable
data class ValuationDto(val id: Long, val assetId: Long, val date: Long, val value: Long, val note: String? = null, val createdAtMs: Long)

@Serializable
data class ExchangeRateDto(
    val id: Long, val base: String, val quote: String, val date: Long, val rateMicros: Long, val source: String, val fetchedAtMs: Long,
)
