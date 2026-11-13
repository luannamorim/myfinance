package io.github.luannamorim.myfinance.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.luannamorim.myfinance.data.db.entity.AssetCategoryEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetFlowEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetValuationEntity
import io.github.luannamorim.myfinance.data.db.entity.CashAdjustmentEntity
import io.github.luannamorim.myfinance.data.db.entity.CategoryEntity
import io.github.luannamorim.myfinance.data.db.entity.ExchangeRateEntity
import io.github.luannamorim.myfinance.data.db.entity.OccurrenceEntity
import io.github.luannamorim.myfinance.data.db.entity.TransactionTemplateEntity

/**
 * Bulk operations used only by backup restore, always inside one transaction: children are cleared before their
 * parents and parents are inserted before their children (foreign keys are enforced).
 */
@Dao
interface BackupDao {
    @Query("DELETE FROM transaction_occurrence") suspend fun clearOccurrences()
    @Query("DELETE FROM transaction_template") suspend fun clearTemplates()
    @Query("DELETE FROM category") suspend fun clearCategories()
    @Query("DELETE FROM cash_adjustment") suspend fun clearAdjustments()
    @Query("DELETE FROM asset_valuation") suspend fun clearValuations()
    @Query("DELETE FROM asset_flow") suspend fun clearAssetFlows()
    @Query("DELETE FROM asset") suspend fun clearAssets()
    @Query("DELETE FROM asset_category") suspend fun clearAssetCategories()
    @Query("DELETE FROM exchange_rate") suspend fun clearExchangeRates()

    @Insert suspend fun insertCategories(items: List<CategoryEntity>)
    @Insert suspend fun insertTemplates(items: List<TransactionTemplateEntity>)
    @Insert suspend fun insertOccurrences(items: List<OccurrenceEntity>)
    @Insert suspend fun insertAdjustments(items: List<CashAdjustmentEntity>)
    @Insert suspend fun insertAssetCategories(items: List<AssetCategoryEntity>)
    @Insert suspend fun insertAssets(items: List<AssetEntity>)
    @Insert suspend fun insertAssetFlows(items: List<AssetFlowEntity>)
    @Insert suspend fun insertValuations(items: List<AssetValuationEntity>)
    @Insert suspend fun insertExchangeRates(items: List<ExchangeRateEntity>)
}
