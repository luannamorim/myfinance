package io.github.luannamorim.myfinance.platform.backup

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import io.github.luannamorim.myfinance.data.backup.BackupDestination
import io.github.luannamorim.myfinance.data.backup.BackupDestinationFactory
import io.github.luannamorim.myfinance.data.backup.FileBackupDestination
import io.github.luannamorim.myfinance.data.backup.FolderBackupWriter
import io.github.luannamorim.myfinance.data.prefs.BackupFailure
import io.github.luannamorim.myfinance.data.prefs.BackupStateStore
import io.github.luannamorim.myfinance.domain.backup.BackupError
import io.github.luannamorim.myfinance.domain.backup.BackupFileInfo
import io.github.luannamorim.myfinance.testutil.BackupEnv
import io.github.luannamorim.myfinance.testutil.TEST_ITERATIONS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
/** Plain Application: keeps MyFinanceApp's startup work (restore cleanup, image orphans) out of these tests. */
@Config(sdk = [36], application = Application::class)
class BackupWorkerTest {
    private val env = BackupEnv()
    private val context: Context = env.context
    private val keyStore = env.keyStore
    private val writer = FolderBackupWriter(env.manager(), env.time)
    private val dir: File = Files.createTempDirectory("backups").toFile()
    private val store = BackupStateStore(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { File(Files.createTempDirectory("state").toFile(), "s.preferences_pb") },
    )
    private var factory: BackupDestinationFactory = BackupDestinationFactory { FileBackupDestination(dir, env.time.zone()) }
    private val notifications = shadowOf(context.getSystemService(android.app.NotificationManager::class.java))

    @After fun tearDown() = env.close()

    private fun worker(force: Boolean = false, attempt: Int = 0): BackupWorker =
        TestListenableWorkerBuilder<BackupWorker>(context)
            .setInputData(workDataOf(BackupWorker.KEY_FORCE to force))
            .setRunAttemptCount(attempt)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    BackupWorker(appContext, workerParameters, store, env.settings, keyStore, factory, writer, BackupNotifier(appContext), env.time)
            })
            .build()

    private suspend fun configured() {
        keyStore.setPassword("senha".toCharArray(), TEST_ITERATIONS)
        store.setFolder("folder", "MyFinance Backup")
    }

    private fun files() = dir.list().orEmpty().filter { it.endsWith(".myfinance") }

    @Test
    fun noFolder_automatic_skipsSilently() = runBlocking {
        keyStore.setPassword("senha".toCharArray(), TEST_ITERATIONS)
        store.markChanged()
        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        assertNull(store.current().lastError)
        assertTrue(files().isEmpty())
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun noPassword_automatic_skipsSilently() = runBlocking {
        store.setFolder("folder", null)
        store.markChanged()
        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        val state = store.current()
        assertNull(state.lastError)
        assertNull(state.lastAttemptMs)
        assertTrue("still pending for when it is configured", state.hasUnsavedChanges)
        assertTrue(files().isEmpty())
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun autoBackupOff_automatic_skips() = runBlocking {
        configured()
        env.settings.update { it.copy(autoBackup = false) }
        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        assertTrue(files().isEmpty())
    }

    @Test
    fun clean_notForced_skips() = runBlocking {
        configured()
        assertTrue(worker(force = true).doWork() is ListenableWorker.Result.Success)
        assertEquals(1, files().size)
        env.time.nowMs += 60_000
        assertTrue(worker().doWork() is ListenableWorker.Result.Success) // nothing changed since
        assertEquals(1, files().size)
    }

    @Test
    fun dirty_writes_andClearsDirty() = runBlocking {
        configured()
        store.markChanged()
        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        val state = store.current()
        assertFalse(state.hasUnsavedChanges)
        assertEquals(files().single(), state.lastFileName)
        assertEquals(env.time.nowMs, state.lastSuccessMs)
        assertNull(state.lastError)
    }

    @Test
    fun changeDuringRun_keepsDirty() = runBlocking {
        configured()
        store.markChanged()
        factory = BackupDestinationFactory {
            val real = FileBackupDestination(dir, env.time.zone())
            object : BackupDestination by real {
                override suspend fun write(name: String, block: suspend (OutputStream) -> Unit): BackupFileInfo {
                    store.markChanged() // the user edits something while the backup is being written
                    return real.write(name, block)
                }
            }
        }
        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        assertTrue(store.current().hasUnsavedChanges)
    }

    @Test
    fun folderUnavailable_setsError_keepsDirty_noRetry() = runBlocking {
        configured()
        store.markChanged()
        factory = BackupDestinationFactory { throw BackupError.FolderUnavailable("revoked") }
        assertTrue(worker().doWork() is ListenableWorker.Result.Failure)
        assertEquals(BackupFailure.FOLDER_UNAVAILABLE, store.current().lastError)
        assertTrue(store.current().hasUnsavedChanges)
    }

    @Test
    fun verificationFailure_reportsUnknown_andKeepsDirty() = runBlocking {
        configured()
        store.markChanged()
        factory = BackupDestinationFactory {
            val real = FileBackupDestination(dir, env.time.zone())
            object : BackupDestination by real {
                override suspend fun open(file: BackupFileInfo): InputStream = "not a backup at all, definitely not one".byteInputStream()
            }
        }
        assertTrue(worker().doWork() is ListenableWorker.Result.Failure)
        assertEquals(BackupFailure.UNKNOWN, store.current().lastError)
        assertTrue(store.current().hasUnsavedChanges)
        assertEquals(1, notifications.allNotifications.size)
    }

    @Test
    fun ioError_retries_thenFails() = runBlocking {
        configured()
        store.markChanged()
        factory = BackupDestinationFactory {
            val real = FileBackupDestination(dir, env.time.zone())
            object : BackupDestination by real {
                override suspend fun write(name: String, block: suspend (OutputStream) -> Unit): BackupFileInfo = throw IOException("provider hiccup")
            }
        }
        assertTrue(worker(attempt = 0).doWork() is ListenableWorker.Result.Retry)
        assertNull(store.current().lastError)
        assertTrue(worker(attempt = 3).doWork() is ListenableWorker.Result.Failure)
        assertEquals(BackupFailure.UNKNOWN, store.current().lastError)
    }

    @Test
    fun noSpace_isReported() = runBlocking {
        configured()
        store.markChanged()
        factory = BackupDestinationFactory {
            val real = FileBackupDestination(dir, env.time.zone())
            object : BackupDestination by real {
                override suspend fun write(name: String, block: suspend (OutputStream) -> Unit): BackupFileInfo = throw IOException("write failed: ENOSPC")
            }
        }
        assertTrue(worker().doWork() is ListenableWorker.Result.Failure)
        assertEquals(BackupFailure.NO_SPACE, store.current().lastError)
    }

    @Test
    fun forced_withoutPassword_reportsNoPassword() = runBlocking {
        store.setFolder("folder", null)
        assertTrue(worker(force = true).doWork() is ListenableWorker.Result.Failure)
        assertEquals(BackupFailure.NO_PASSWORD, store.current().lastError)
    }

    @Test
    fun forced_withoutFolder_reportsNoFolder() = runBlocking {
        assertTrue(worker(force = true).doWork() is ListenableWorker.Result.Failure)
        assertEquals(BackupFailure.NO_FOLDER, store.current().lastError)
    }

    @Test
    fun successAfterFailure_clearsError() = runBlocking {
        configured()
        factory = BackupDestinationFactory { throw BackupError.FolderUnavailable("revoked") }
        assertTrue(worker(force = true).doWork() is ListenableWorker.Result.Failure)
        factory = BackupDestinationFactory { FileBackupDestination(dir, env.time.zone()) }
        assertTrue(worker(force = true).doWork() is ListenableWorker.Result.Success)
        assertNull(store.current().lastError)
    }

    @Test
    fun onDataChanged_marksChanged_andEnqueuesTenMinuteDebounce() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val scheduler = BackupScheduler(context, store, CoroutineScope(Dispatchers.Unconfined))
        scheduler.onDataChanged()
        assertTrue(store.current().hasUnsavedChanges)
        val wm = WorkManager.getInstance(context)
        val info = wm.getWorkInfosForUniqueWork(BackupScheduler.DEBOUNCE).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertTrue(info.initialDelayMillis >= TimeUnit.MINUTES.toMillis(10))

        // A second change replaces the pending run instead of adding one.
        scheduler.onDataChanged()
        assertEquals(1, wm.getWorkInfosForUniqueWork(BackupScheduler.DEBOUNCE).get().count { it.state == WorkInfo.State.ENQUEUED })

        scheduler.cancelPending()
        assertTrue(wm.getWorkInfosForUniqueWork(BackupScheduler.DEBOUNCE).get().all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun ensurePeriodic_andRunNow_useTheirUniqueNames() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val scheduler = BackupScheduler(context, store, CoroutineScope(Dispatchers.Unconfined))
        scheduler.ensurePeriodic()
        scheduler.ensurePeriodic()
        val wm = WorkManager.getInstance(context)
        assertEquals(1, wm.getWorkInfosForUniqueWork(BackupScheduler.DAILY).get().size)
        scheduler.runNow()
        assertEquals(1, wm.getWorkInfosForUniqueWork(BackupScheduler.NOW).get().size)
        assertEquals("backup-debounce", BackupScheduler.DEBOUNCE)
        assertEquals("backup-run", BackupScheduler.RUN)
        assertEquals("backup-daily", BackupScheduler.DAILY)
        assertEquals("backup-now", BackupScheduler.NOW)
    }
}
