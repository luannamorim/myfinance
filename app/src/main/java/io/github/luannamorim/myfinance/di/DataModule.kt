package io.github.luannamorim.myfinance.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.luannamorim.myfinance.data.backup.AndroidKeystoreKeyWrapper
import io.github.luannamorim.myfinance.data.backup.BackupDestinationFactory
import io.github.luannamorim.myfinance.data.backup.KeyWrapper
import io.github.luannamorim.myfinance.data.backup.SafBackupDestinationFactory
import io.github.luannamorim.myfinance.data.repository.BackupTrigger
import io.github.luannamorim.myfinance.platform.backup.BackupScheduler
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    @Named("settings")
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    @Named("rate_state")
    fun provideRateStateDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("rate_state") }

    @Provides
    @Singleton
    @Named("backup_key")
    fun provideBackupKeyDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("backup_key") }

    @Provides
    @Singleton
    @Named("backup_state")
    fun provideBackupStateDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("backup_state") }

    @Provides
    fun provideKeyWrapper(impl: AndroidKeystoreKeyWrapper): KeyWrapper = impl

    @Provides
    fun provideBackupDestinationFactory(impl: SafBackupDestinationFactory): BackupDestinationFactory = impl

    /** Every data change marks the backup state dirty and schedules the debounced run. */
    @Provides
    fun provideBackupTrigger(scheduler: BackupScheduler): BackupTrigger = scheduler
}
