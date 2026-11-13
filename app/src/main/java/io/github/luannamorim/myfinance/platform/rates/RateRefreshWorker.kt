package io.github.luannamorim.myfinance.platform.rates

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.luannamorim.myfinance.data.prefs.RateError
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.data.repository.RefreshResult
import java.util.concurrent.TimeUnit

/** Daily PTAX refresh when the network is available (respects the "Fetch exchange rates" switch). */
@HiltWorker
class RateRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val rates: ExchangeRateRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (val result = rates.refresh()) {
        is RefreshResult.Failed ->
            if (result.reason == RateError.NETWORK && runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        else -> Result.success()
    }

    companion object {
        const val NAME = "rates-daily"
        const val MAX_RETRIES = 3

        fun schedule(context: Context) {
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<RateRefreshWorker>(1, TimeUnit.DAYS)
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build(),
                )
            }
        }
    }
}
