package io.github.luannamorim.myfinance.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.luannamorim.myfinance.data.db.entity.AssetEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetFlowEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetValuationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioDao {

    @Query("SELECT * FROM asset ORDER BY name COLLATE NOCASE, id")
    fun observeAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM asset_flow ORDER BY date, id")
    fun observeFlows(): Flow<List<AssetFlowEntity>>

    @Query("SELECT * FROM asset_valuation ORDER BY date, id")
    fun observeValuations(): Flow<List<AssetValuationEntity>>

    @Query("SELECT * FROM asset WHERE id = :id")
    suspend fun getAsset(id: Long): AssetEntity?

    @Insert
    suspend fun insertAsset(asset: AssetEntity): Long

    @Update
    suspend fun updateAsset(asset: AssetEntity)

    @Query("DELETE FROM asset WHERE id = :id")
    suspend fun deleteAsset(id: Long)

    @Query("SELECT * FROM asset_flow WHERE assetId = :assetId ORDER BY date, id")
    suspend fun getFlows(assetId: Long): List<AssetFlowEntity>

    @Query("SELECT * FROM asset_flow WHERE id = :id")
    suspend fun getFlow(id: Long): AssetFlowEntity?

    @Insert
    suspend fun insertFlow(flow: AssetFlowEntity): Long

    @Query("DELETE FROM asset_flow WHERE id = :id")
    suspend fun deleteFlow(id: Long)

    @Update
    suspend fun updateFlow(flow: AssetFlowEntity)

    /** The flow a confirmed transfer created, if any. */
    @Query("SELECT * FROM asset_flow WHERE occurrenceId = :occurrenceId")
    suspend fun getFlowByOccurrence(occurrenceId: Long): AssetFlowEntity?

    @Query("SELECT * FROM asset_valuation WHERE assetId = :assetId ORDER BY date, id")
    suspend fun getValuations(assetId: Long): List<AssetValuationEntity>

    @Query("SELECT * FROM asset_valuation WHERE assetId = :assetId AND date = :date")
    suspend fun getValuationOn(assetId: Long, date: Long): AssetValuationEntity?

    @Insert
    suspend fun insertValuation(valuation: AssetValuationEntity): Long

    @Update
    suspend fun updateValuation(valuation: AssetValuationEntity)

    @Query("DELETE FROM asset_valuation WHERE id = :id")
    suspend fun deleteValuation(id: Long)

    @Query("SELECT * FROM asset ORDER BY id")
    suspend fun getAllAssets(): List<AssetEntity>

    @Query("SELECT * FROM asset_flow ORDER BY id")
    suspend fun getAllFlows(): List<AssetFlowEntity>

    @Query("SELECT * FROM asset_valuation ORDER BY id")
    suspend fun getAllValuations(): List<AssetValuationEntity>

    @Query("SELECT imagePath FROM asset WHERE imagePath IS NOT NULL")
    suspend fun imagePaths(): List<String>
}
