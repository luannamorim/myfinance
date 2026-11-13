package io.github.luannamorim.myfinance.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.luannamorim.myfinance.data.db.dao.BackupDao
import io.github.luannamorim.myfinance.data.db.dao.CashDao
import io.github.luannamorim.myfinance.data.db.dao.CategoryDao
import io.github.luannamorim.myfinance.data.db.dao.PortfolioDao
import io.github.luannamorim.myfinance.data.db.dao.RateDao
import io.github.luannamorim.myfinance.data.db.entity.AssetCategoryEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetFlowEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetValuationEntity
import io.github.luannamorim.myfinance.data.db.entity.CashAdjustmentEntity
import io.github.luannamorim.myfinance.data.db.entity.CategoryEntity
import io.github.luannamorim.myfinance.data.db.entity.ExchangeRateEntity
import io.github.luannamorim.myfinance.data.db.entity.OccurrenceEntity
import io.github.luannamorim.myfinance.data.db.entity.TransactionTemplateEntity

@Database(
    entities = [
        CategoryEntity::class,
        TransactionTemplateEntity::class,
        OccurrenceEntity::class,
        CashAdjustmentEntity::class,
        AssetCategoryEntity::class,
        AssetEntity::class,
        AssetFlowEntity::class,
        AssetValuationEntity::class,
        ExchangeRateEntity::class,
    ],
    version = MyFinanceDatabase.VERSION,
    exportSchema = true,
)
abstract class MyFinanceDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun cashDao(): CashDao
    abstract fun portfolioDao(): PortfolioDao
    abstract fun rateDao(): RateDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val VERSION = 1
        const val NAME = "myfinance.db"
    }
}
