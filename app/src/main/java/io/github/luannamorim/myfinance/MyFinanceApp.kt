package io.github.luannamorim.myfinance

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import io.github.luannamorim.myfinance.data.backup.BackupManager
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.di.ApplicationScope
import io.github.luannamorim.myfinance.platform.backup.BackupScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MyFinanceApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var cash: Lazy<CashRepository>
    @Inject lateinit var backupManager: Lazy<BackupManager>
    @Inject lateinit var backupScheduler: Lazy<BackupScheduler>
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            // Leftovers of a restore interrupted by process death; then the daily backup.
            backupManager.get().recoverInterruptedRestore()
            backupScheduler.get().ensurePeriodic()
        }
        appScope.launch {
            // Keep 12 months of occurrences ahead of today.
            cash.get().ensureGenerated()
        }
        // Rates are fetched when the app comes to the foreground (MainActivity) or with "Update". 1.0.0 also
        // scheduled a daily background job: cancel it on installs updated from that version.
        runCatching { WorkManager.getInstance(this).cancelUniqueWork(LEGACY_RATE_WORK) }
    }

    private companion object {
        /** Unique name of the daily rate job scheduled by 1.0.0. */
        const val LEGACY_RATE_WORK = "rates-daily"
    }
}
