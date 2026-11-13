package io.github.luannamorim.myfinance.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.luannamorim.myfinance.data.db.entity.ExchangeRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RateDao {

    @Query("SELECT * FROM exchange_rate ORDER BY date, source")
    fun observeAll(): Flow<List<ExchangeRateEntity>>

    /** Fetched rates: an existing row for the same date and source wins. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(rates: List<ExchangeRateEntity>): List<Long>

    /** A manual rate replaces the previous manual rate of that date (never the BCB one). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertManual(rate: ExchangeRateEntity): Long

    @Query("DELETE FROM exchange_rate WHERE source = 'MANUAL' AND date = :date")
    suspend fun deleteManual(date: Long)

    @Query("SELECT MAX(date) FROM exchange_rate WHERE source = 'BCB'")
    suspend fun latestBcbDate(): Long?

    @Query("SELECT MIN(date) FROM exchange_rate WHERE source = 'BCB'")
    suspend fun firstBcbDate(): Long?

    @Query("SELECT * FROM exchange_rate ORDER BY id")
    suspend fun getAll(): List<ExchangeRateEntity>
}
