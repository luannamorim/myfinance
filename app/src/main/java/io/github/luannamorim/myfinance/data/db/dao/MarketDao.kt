package io.github.luannamorim.myfinance.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.luannamorim.myfinance.data.db.entity.AssetPriceEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetSplitEntity

@Dao
interface MarketDao {

    @Query("SELECT * FROM asset_price ORDER BY symbol, date")
    suspend fun listPrices(): List<AssetPriceEntity>

    @Query("SELECT * FROM asset_split ORDER BY symbol, date")
    suspend fun listSplits(): List<AssetSplitEntity>

    /** A refetched day replaces the stored close of that day. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrices(items: List<AssetPriceEntity>)

    /** Splits already stored (same symbol and date) are kept. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSplits(items: List<AssetSplitEntity>): List<Long>

    @Query("SELECT date FROM asset_price WHERE symbol = :symbol ORDER BY date")
    suspend fun priceDates(symbol: String): List<Long>

    @Query("SELECT MIN(date) FROM asset_price WHERE symbol = :symbol")
    suspend fun firstPriceDate(symbol: String): Long?

    @Query("SELECT MAX(date) FROM asset_price WHERE symbol = :symbol")
    suspend fun lastPriceDate(symbol: String): Long?

    @Query("SELECT * FROM asset_split WHERE symbol = :symbol ORDER BY date")
    suspend fun splitsOf(symbol: String): List<AssetSplitEntity>

    @Query("DELETE FROM asset_price WHERE symbol NOT IN (SELECT ticker FROM asset WHERE ticker IS NOT NULL)")
    suspend fun pruneUnusedPrices()

    @Query("DELETE FROM asset_split WHERE symbol NOT IN (SELECT ticker FROM asset WHERE ticker IS NOT NULL)")
    suspend fun pruneUnusedSplits()

    /** Drops quotes of symbols no asset tracks any more. */
    @androidx.room.Transaction
    suspend fun pruneUnused() {
        pruneUnusedPrices()
        pruneUnusedSplits()
    }
}
