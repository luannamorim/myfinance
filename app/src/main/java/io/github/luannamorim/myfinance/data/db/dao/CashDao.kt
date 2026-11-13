package io.github.luannamorim.myfinance.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.luannamorim.myfinance.data.db.entity.CashAdjustmentEntity
import io.github.luannamorim.myfinance.data.db.entity.OccurrenceEntity
import io.github.luannamorim.myfinance.data.db.entity.TransactionTemplateEntity
import io.github.luannamorim.myfinance.domain.model.Currency
import kotlinx.coroutines.flow.Flow

@Dao
interface CashDao {

    // Templates

    @Query("SELECT * FROM transaction_template ORDER BY name COLLATE NOCASE, id")
    fun observeTemplates(): Flow<List<TransactionTemplateEntity>>

    @Query("SELECT * FROM transaction_template WHERE id = :id")
    suspend fun getTemplate(id: Long): TransactionTemplateEntity?

    @Query("SELECT * FROM transaction_template WHERE active = 1")
    suspend fun getActiveTemplates(): List<TransactionTemplateEntity>

    @Insert
    suspend fun insertTemplate(template: TransactionTemplateEntity): Long

    @Update
    suspend fun updateTemplate(template: TransactionTemplateEntity)

    @Query("DELETE FROM transaction_template WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Query("SELECT * FROM transaction_template ORDER BY id")
    suspend fun getAllTemplates(): List<TransactionTemplateEntity>

    // Occurrences

    @Query("SELECT * FROM transaction_occurrence ORDER BY dueDate, id")
    fun observeOccurrences(): Flow<List<OccurrenceEntity>>

    @Query("SELECT * FROM transaction_occurrence WHERE id = :id")
    suspend fun getOccurrence(id: Long): OccurrenceEntity?

    @Query("SELECT * FROM transaction_occurrence WHERE templateId = :templateId ORDER BY dueDate, id")
    suspend fun getOccurrences(templateId: Long): List<OccurrenceEntity>

    @Insert
    suspend fun insertOccurrence(occurrence: OccurrenceEntity): Long

    /** Inserts generated occurrences, skipping any (templateId, scheduledDate) that already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrencesIgnore(occurrences: List<OccurrenceEntity>): List<Long>

    @Update
    suspend fun updateOccurrence(occurrence: OccurrenceEntity)

    @Query("DELETE FROM transaction_occurrence WHERE id = :id")
    suspend fun deleteOccurrence(id: Long)

    /** Pending occurrences of a template due on or after [fromDay]; customized ones only when asked. */
    @Query(
        "DELETE FROM transaction_occurrence WHERE templateId = :templateId AND state = 'PENDING' " +
            "AND dueDate >= :fromDay AND (:includeCustomized OR customized = 0)",
    )
    suspend fun deleteReplaceable(templateId: Long, fromDay: Long, includeCustomized: Boolean): Int

    /** Every pending occurrence after a (new) end date, customized or not. */
    @Query("DELETE FROM transaction_occurrence WHERE templateId = :templateId AND state = 'PENDING' AND scheduledDate > :endDay")
    suspend fun deletePendingAfter(templateId: Long, endDay: Long): Int

    @Query("SELECT COUNT(*) FROM transaction_occurrence WHERE templateId = :templateId AND state = 'PENDING' AND dueDate >= :fromDay AND customized = 1")
    suspend fun countCustomizedReplaceable(templateId: Long, fromDay: Long): Int

    @Query("SELECT * FROM transaction_occurrence ORDER BY id")
    suspend fun getAllOccurrences(): List<OccurrenceEntity>

    /** Recurring transfers to or from an asset. */
    @Query("SELECT * FROM transaction_template WHERE assetId = :assetId")
    suspend fun getTemplatesForAsset(assetId: Long): List<TransactionTemplateEntity>

    /** Transfers to or from an asset that haven't happened (they can't any more once the asset is gone). */
    @Query("DELETE FROM transaction_occurrence WHERE assetId = :assetId AND state = 'PENDING'")
    suspend fun deletePendingForAsset(assetId: Long): Int

    // Adjustments

    @Query("SELECT * FROM cash_adjustment ORDER BY date, id")
    fun observeAdjustments(): Flow<List<CashAdjustmentEntity>>

    @Query("SELECT * FROM cash_adjustment WHERE kind = 'OPENING' AND currency = :currency")
    suspend fun getOpening(currency: Currency): CashAdjustmentEntity?

    @Query("SELECT MIN(date) FROM cash_adjustment WHERE kind = 'ADJUSTMENT' AND currency = :currency")
    suspend fun firstAdjustmentDate(currency: Currency): Long?

    @Insert
    suspend fun insertAdjustment(adjustment: CashAdjustmentEntity): Long

    @Update
    suspend fun updateAdjustment(adjustment: CashAdjustmentEntity)

    @Query("DELETE FROM cash_adjustment WHERE id = :id")
    suspend fun deleteAdjustment(id: Long)

    @Query("SELECT * FROM cash_adjustment ORDER BY id")
    suspend fun getAllAdjustments(): List<CashAdjustmentEntity>
}
