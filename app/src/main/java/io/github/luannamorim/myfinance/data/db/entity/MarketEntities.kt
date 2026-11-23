package io.github.luannamorim.myfinance.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.luannamorim.myfinance.domain.model.PriceSource

/** Raw (not split-adjusted) close of [symbol] on [date] (epoch day). `price` is a plain decimal string. */
@Entity(tableName = "asset_price", indices = [Index(value = ["symbol", "date"], unique = true)])
data class AssetPriceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val date: Long,
    val price: String,
    val source: PriceSource,
    val fetchedAtMs: Long,
)

/** A split of [symbol] on [date]: each share becomes numerator ÷ denominator shares (decimal strings). */
@Entity(tableName = "asset_split", indices = [Index(value = ["symbol", "date"], unique = true)])
data class AssetSplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val date: Long,
    val numerator: String,
    val denominator: String,
)
