package io.github.luannamorim.myfinance.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.RateSource

@Entity(
    tableName = "asset_category",
    indices = [Index(value = ["seedKey"], unique = true), Index("assetGroup")],
)
data class AssetCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String?,
    val assetGroup: AssetGroup,
    val icon: String,
    val seedKey: String? = null,
    val archived: Boolean = false,
    val position: Int,
    val createdAtMs: Long,
)

/** An asset. Flows and valuations use its `currency`, which is locked after creation. `quantity` is a decimal string. */
@Entity(
    tableName = "asset",
    foreignKeys = [
        ForeignKey(entity = AssetCategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("categoryId")],
)
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long,
    val currency: Currency,
    val description: String? = null,
    val institution: String? = null,
    val acquisitionDate: Long,
    val quantity: String? = null,
    val notes: String? = null,
    val imagePath: String? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

/** Contribution, withdrawal or disposal; `amount` > 0 (INITIAL may be 0), direction given by `kind`. */
@Entity(
    tableName = "asset_flow",
    foreignKeys = [
        ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["assetId", "date"])],
)
data class AssetFlowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val kind: FlowKind,
    val date: Long,
    val amount: Long,
    val quantityDelta: String? = null,
    val note: String? = null,
    val createdAtMs: Long,
)

/** Estimated value on a date; one per asset and day. */
@Entity(
    tableName = "asset_valuation",
    foreignKeys = [
        ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["assetId", "date"], unique = true)],
)
data class AssetValuationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val date: Long,
    val value: Long,
    val note: String? = null,
    val createdAtMs: Long,
)

/** USD→BRL rate × 10⁶ for a date; a BCB and a MANUAL rate can coexist on the same date. */
@Entity(
    tableName = "exchange_rate",
    indices = [Index(value = ["base", "quote", "date", "source"], unique = true)],
)
data class ExchangeRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val base: Currency,
    val quote: Currency,
    val date: Long,
    val rateMicros: Long,
    val source: RateSource,
    val fetchedAtMs: Long,
)
