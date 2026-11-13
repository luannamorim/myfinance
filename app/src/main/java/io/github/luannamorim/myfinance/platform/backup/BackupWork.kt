package io.github.luannamorim.myfinance.platform.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.luannamorim.myfinance.MainActivity
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.data.backup.BackupDestinationFactory
import io.github.luannamorim.myfinance.data.backup.BackupKeyStore
import io.github.luannamorim.myfinance.data.backup.FolderBackupWriter
import io.github.luannamorim.myfinance.data.prefs.BackupFailure
import io.github.luannamorim.myfinance.data.prefs.BackupStateStore
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.data.repository.BackupTrigger
import io.github.luannamorim.myfinance.di.ApplicationScope
import io.github.luannamorim.myfinance.domain.backup.BackupError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules backups: daily, a few minutes after important changes, and on demand. */
@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: BackupStateStore,
    @ApplicationScope private val scope: CoroutineScope,
) : BackupTrigger {

    // WorkManager may be absent in some test environments; scheduling is best-effort there.
    private fun workManager(): WorkManager? = runCatching { WorkManager.getInstance(context) }.getOrNull()

    override fun onDataChanged() {
        scope.launch { store.markChanged() }
        workManager()?.enqueueUniqueWork(
            DEBOUNCE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BackupDebounceWorker>().setInitialDelay(DEBOUNCE_MINUTES, TimeUnit.MINUTES).build(),
        )
    }

    override fun cancelPending() {
        workManager()?.apply {
            cancelUniqueWork(DEBOUNCE)
            cancelUniqueWork(RUN)
        }
    }

    fun ensurePeriodic() {
        workManager()?.enqueueUniquePeriodicWork(
            DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
                .build(),
        )
    }

    /** Manual "back up now" (ignores the unsaved-changes check). */
    fun runNow() {
        workManager()?.enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BackupWorker>().setInputData(workDataOf(BackupWorker.KEY_FORCE to true)).build(),
        )
    }

    fun observeManualRun(): Flow<WorkInfo?> =
        workManager()?.getWorkInfosForUniqueWorkFlow(NOW)?.map { it.firstOrNull() } ?: flowOf(null)

    companion object {
        const val DEBOUNCE = "backup-debounce"
        const val RUN = "backup-run"
        const val DAILY = "backup-daily"
        const val NOW = "backup-now"
        const val DEBOUNCE_MINUTES = 10L
    }
}

/** Fires after the debounce delay; runs the actual backup after any one already in progress. */
@HiltWorker
class BackupDebounceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            BackupScheduler.RUN,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<BackupWorker>().build(),
        )
        return Result.success()
    }
}

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val store: BackupStateStore,
    private val settings: SettingsRepository,
    private val keyStore: BackupKeyStore,
    private val destinations: BackupDestinationFactory,
    private val writer: FolderBackupWriter,
    private val notifier: BackupNotifier,
    private val time: TimeSource,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val force = inputData.getBoolean(KEY_FORCE, false)
        val state = store.current()
        if (!force) {
            // Automatic runs stay silent until backups are set up (a folder and a password): "not configured" is not
            // a failure. They also respect the switch and only run when something changed.
            if (state.folderUri == null || !keyStore.hasPassword.first()) return Result.success()
            if (!settings.current().autoBackup || !state.hasUnsavedChanges) return Result.success()
        }
        val folder = state.folderUri ?: return fail(BackupFailure.NO_FOLDER)
        val covered = state.changeCount
        store.recordAttempt(time.nowMs())
        return try {
            val info = writer.run(destinations.create(folder))
            store.recordSuccess(time.nowMs(), info.name, covered)
            notifier.clear()
            Result.success()
        } catch (e: BackupError.NoPassword) {
            fail(BackupFailure.NO_PASSWORD)
        } catch (e: BackupError.FolderUnavailable) {
            fail(BackupFailure.FOLDER_UNAVAILABLE)
        } catch (e: SecurityException) {
            fail(BackupFailure.FOLDER_UNAVAILABLE)
        } catch (e: IOException) {
            if (e.message.orEmpty().contains("ENOSPC") || e.message.orEmpty().contains("No space", ignoreCase = true)) {
                fail(BackupFailure.NO_SPACE)
            } else if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                fail(BackupFailure.UNKNOWN)
            }
        } catch (e: BackupError) {
            fail(BackupFailure.UNKNOWN)
        }
    }

    private suspend fun fail(failure: BackupFailure): Result {
        store.recordFailure(failure)
        notifier.notifyFailure(failure)
        return Result.failure()
    }

    companion object {
        const val KEY_FORCE = "force"
        private const val MAX_RETRIES = 3
    }
}

/** Tells the user when a backup could not be written; tapping opens Settings → Backup. */
@Singleton
class BackupNotifier @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun notifyFailure(failure: BackupFailure) {
        if (!manager.areNotificationsEnabled()) return
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.backup_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val text = context.getString(
            when (failure) {
                BackupFailure.NO_FOLDER, BackupFailure.FOLDER_UNAVAILABLE -> R.string.backup_error_folder
                BackupFailure.NO_PASSWORD -> R.string.backup_error_password
                BackupFailure.NO_SPACE -> R.string.backup_error_space
                BackupFailure.UNKNOWN -> R.string.backup_error_unknown
            },
        )
        val open = PendingIntent.getActivity(
            context,
            30,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_BACKUP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_myfinance)
            .setContentTitle(context.getString(R.string.backup_failed_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        manager.notify(ID, notification)
    }

    fun clear() = manager.cancel(ID)

    private companion object {
        const val CHANNEL = "backup_status"
        const val ID = 2001
    }
}
