package io.github.luannamorim.myfinance.data.repository

import androidx.room.withTransaction
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.data.db.MyFinanceDatabase
import io.github.luannamorim.myfinance.data.db.entity.AssetCategoryEntity
import io.github.luannamorim.myfinance.data.db.entity.CategoryEntity
import io.github.luannamorim.myfinance.domain.model.AssetCategory
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Category
import io.github.luannamorim.myfinance.domain.model.EntryKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class DeleteResult { DELETED, IN_USE }

/** Income/expense and asset categories. A category in use can only be archived, so history stays intact. */
@Singleton
class CategoryRepository @Inject constructor(
    private val db: MyFinanceDatabase,
    private val time: TimeSource,
    private val backup: BackupTrigger,
) {
    private val dao = db.categoryDao()

    fun observe(kind: EntryKind, includeArchived: Boolean = false): Flow<List<Category>> =
        dao.observe(kind, includeArchived).map { list -> list.map { it.toDomain() } }

    /** Every cash category, archived included (for names in history). */
    fun observeAll(): Flow<List<Category>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeAssetCategories(includeArchived: Boolean = false): Flow<List<AssetCategory>> =
        dao.observeAssetCategories(includeArchived).map { list -> list.map { it.toDomain() } }

    suspend fun create(kind: EntryKind, name: String, icon: String): Long {
        require(name.isNotBlank()) { "Name is required" }
        val id = db.withTransaction {
            dao.insert(CategoryEntity(name = name.trim(), kind = kind, icon = icon, position = dao.nextPosition(kind), createdAtMs = time.nowMs()))
        }
        backup.onDataChanged()
        return id
    }

    suspend fun update(id: Long, name: String, icon: String) {
        require(name.isNotBlank()) { "Name is required" }
        val current = checkNotNull(dao.get(id)) { "Category $id not found" }
        dao.update(current.copy(name = name.trim(), icon = icon))
        backup.onDataChanged()
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        val current = checkNotNull(dao.get(id)) { "Category $id not found" }
        dao.update(current.copy(archived = archived))
        backup.onDataChanged()
    }

    suspend fun delete(id: Long): DeleteResult {
        val result = db.withTransaction {
            if (dao.usageCount(id) > 0) DeleteResult.IN_USE else DeleteResult.DELETED.also { dao.delete(id) }
        }
        if (result == DeleteResult.DELETED) backup.onDataChanged()
        return result
    }

    suspend fun createAssetCategory(group: AssetGroup, name: String, icon: String): Long {
        require(name.isNotBlank()) { "Name is required" }
        val id = db.withTransaction {
            dao.insertAssetCategory(
                AssetCategoryEntity(name = name.trim(), assetGroup = group, icon = icon, position = dao.nextAssetPosition(group), createdAtMs = time.nowMs()),
            )
        }
        backup.onDataChanged()
        return id
    }

    suspend fun updateAssetCategory(id: Long, name: String, icon: String) {
        require(name.isNotBlank()) { "Name is required" }
        val current = checkNotNull(dao.getAssetCategory(id)) { "Asset category $id not found" }
        dao.updateAssetCategory(current.copy(name = name.trim(), icon = icon))
        backup.onDataChanged()
    }

    suspend fun setAssetCategoryArchived(id: Long, archived: Boolean) {
        val current = checkNotNull(dao.getAssetCategory(id)) { "Asset category $id not found" }
        dao.updateAssetCategory(current.copy(archived = archived))
        backup.onDataChanged()
    }

    suspend fun deleteAssetCategory(id: Long): DeleteResult {
        val result = db.withTransaction {
            if (dao.assetUsageCount(id) > 0) DeleteResult.IN_USE else DeleteResult.DELETED.also { dao.deleteAssetCategory(id) }
        }
        if (result == DeleteResult.DELETED) backup.onDataChanged()
        return result
    }
}
