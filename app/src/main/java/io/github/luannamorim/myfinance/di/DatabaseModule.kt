package io.github.luannamorim.myfinance.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.luannamorim.myfinance.data.db.Migrations
import io.github.luannamorim.myfinance.data.db.MyFinanceDatabase
import io.github.luannamorim.myfinance.data.db.seed.CategorySeed
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MyFinanceDatabase =
        Room.databaseBuilder(context, MyFinanceDatabase::class.java, MyFinanceDatabase.NAME)
            .addCallback(CategorySeed.Callback())
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides fun provideCategoryDao(db: MyFinanceDatabase) = db.categoryDao()
    @Provides fun provideCashDao(db: MyFinanceDatabase) = db.cashDao()
    @Provides fun providePortfolioDao(db: MyFinanceDatabase) = db.portfolioDao()
    @Provides fun provideRateDao(db: MyFinanceDatabase) = db.rateDao()
}
