package io.github.luannamorim.myfinance.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.luannamorim.myfinance.data.db.entity.AssetCategoryEntity
import io.github.luannamorim.myfinance.data.db.entity.CategoryEntity
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.EntryKind
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    // Income and expense categories

    @Query("SELECT * FROM category WHERE kind = :kind AND (:includeArchived OR archived = 0) ORDER BY position, id")
    fun observe(kind: EntryKind, includeArchived: Boolean): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category ORDER BY kind, position, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM category WHERE kind = :kind")
    suspend fun nextPosition(kind: EntryKind): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM category WHERE id = :id")
    suspend fun delete(id: Long)

    /** Templates and occurrences that reference the category (deleting it would break them). */
    @Query(
        "SELECT (SELECT COUNT(*) FROM transaction_template WHERE categoryId = :id) + " +
            "(SELECT COUNT(*) FROM transaction_occurrence WHERE categoryId = :id)",
    )
    suspend fun usageCount(id: Long): Int

    @Query("SELECT * FROM category ORDER BY id")
    suspend fun getAll(): List<CategoryEntity>

    // Asset categories

    @Query("SELECT * FROM asset_category WHERE (:includeArchived OR archived = 0) ORDER BY assetGroup, position, id")
    fun observeAssetCategories(includeArchived: Boolean): Flow<List<AssetCategoryEntity>>

    @Query("SELECT * FROM asset_category WHERE (:includeArchived OR archived = 0) ORDER BY assetGroup, position, id")
    suspend fun listAssetCategories(includeArchived: Boolean): List<AssetCategoryEntity>

    @Query("SELECT * FROM asset_category WHERE id = :id")
    suspend fun getAssetCategory(id: Long): AssetCategoryEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM asset_category WHERE assetGroup = :group")
    suspend fun nextAssetPosition(group: AssetGroup): Int

    @Insert
    suspend fun insertAssetCategory(category: AssetCategoryEntity): Long

    @Update
    suspend fun updateAssetCategory(category: AssetCategoryEntity)

    @Query("DELETE FROM asset_category WHERE id = :id")
    suspend fun deleteAssetCategory(id: Long)

    @Query("SELECT COUNT(*) FROM asset WHERE categoryId = :id")
    suspend fun assetUsageCount(id: Long): Int

    @Query("SELECT * FROM asset_category ORDER BY id")
    suspend fun getAllAssetCategories(): List<AssetCategoryEntity>
}
