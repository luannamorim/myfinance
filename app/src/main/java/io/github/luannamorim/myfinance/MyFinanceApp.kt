package io.github.luannamorim.myfinance

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import io.github.luannamorim.myfinance.data.backup.BackupManager
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.di.ApplicationScope
import io.github.luannamorim.myfinance.platform.backup.BackupScheduler
import io.github.luannamorim.myfinance.platform.rates.RateRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MyFinanceApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var cash: Lazy<CashRepository>
    @Inject lateinit var rates: Lazy<ExchangeRateRepository>
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
        appScope.launch {
            // Throttled to every 6 hours and skipped when automatic fetching is off.
            rates.get().refresh()
            RateRefreshWorker.schedule(this@MyFinanceApp)
        }
    }
}
