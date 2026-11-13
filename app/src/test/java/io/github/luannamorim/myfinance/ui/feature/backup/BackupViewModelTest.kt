package io.github.luannamorim.myfinance.ui.feature.backup

import android.app.Application
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.data.backup.BackupDestinationFactory
import io.github.luannamorim.myfinance.data.backup.FileBackupDestination
import io.github.luannamorim.myfinance.data.backup.KeyState
import io.github.luannamorim.myfinance.data.backup.SnapshotMapper
import io.github.luannamorim.myfinance.data.prefs.BackupStateStore
import io.github.luannamorim.myfinance.domain.backup.BackupDataV1
import io.github.luannamorim.myfinance.domain.backup.BackupError
import io.github.luannamorim.myfinance.platform.backup.BackupScheduler
import io.github.luannamorim.myfinance.testutil.BackupEnv
import io.github.luannamorim.myfinance.testutil.MainDispatcherRule
import io.github.luannamorim.myfinance.testutil.TEST_ITERATIONS
import io.github.luannamorim.myfinance.testutil.awaitUntil
import io.github.luannamorim.myfinance.testutil.populateEverything
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** Plain Application: keeps MyFinanceApp's startup work (restore cleanup, image orphans) out of these tests. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class BackupViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val env = BackupEnv()
    private val keyStore = env.keyStore
    private val manager = env.manager()
    private val dir: File = Files.createTempDirectory("vm").toFile()
    private val store = BackupStateStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { File(dir, "state.preferences_pb") })
    private val scheduler = BackupScheduler(env.context, store, CoroutineScope(Dispatchers.Unconfined))

    // The in-memory database is not closed: ViewModel collectors may still be querying it when the test ends.

    private fun vm(destinations: BackupDestinationFactory = BackupDestinationFactory { FileBackupDestination(File(it)) }) =
        BackupViewModel(env.context, manager, keyStore, store, env.settings, destinations, scheduler)

    private fun backupFile(): Uri {
        val file = File(dir, "b.myfinance")
        runBlocking { file.outputStream().use { manager.create(it) } }
        return Uri.fromFile(file)
    }

    @Test
    fun setPassword_makesKeyReady_andMarksChanged() = runBlocking {
        val vm = vm()
        assertEquals(KeyState.NONE, vm.state.awaitUntil { !it.loading }.keyState)
        vm.setPassword("senha123".toCharArray())
        vm.state.awaitUntil { it.keyState == KeyState.READY && !it.busy }
        assertEquals(KeyState.READY, keyStore.state())
        // The scheduler marks the change on its own scope: wait for it.
        assertTrue(store.state.awaitUntil { it.hasUnsavedChanges }.hasUnsavedChanges)
    }

    @Test
    fun export_withoutPassword_reportsNoPassword() = runBlocking {
        val vm = vm()
        vm.state.awaitUntil { !it.loading }
        vm.exportTo(Uri.fromFile(File(dir, "out.myfinance")))
        assertEquals(BackupMessage.NO_PASSWORD, vm.state.awaitUntil { it.message != null }.message)
        vm.consumeMessage()
        assertEquals(null, vm.state.awaitUntil { it.message == null }.message)
    }

    @Test
    fun runNow_withoutPassword_reportsNoPassword() = runBlocking {
        val vm = vm()
        vm.state.awaitUntil { !it.loading }
        vm.runNow()
        assertEquals(BackupMessage.NO_PASSWORD, vm.state.awaitUntil { it.message != null }.message)
    }

    @Test
    fun export_thenRestore_showsBackupDateBeforeConfirm_andRestoresData() = runBlocking {
        keyStore.setPassword("senha123".toCharArray(), TEST_ITERATIONS)
        env.populateEverything()
        val before = SnapshotMapper.export(env.db)
        val vm = vm()
        vm.state.awaitUntil { it.keyState == KeyState.READY }
        val out = File(dir, "exported.myfinance")
        vm.exportTo(Uri.fromFile(out))
        assertEquals(BackupMessage.EXPORTED, vm.state.awaitUntil { it.message != null }.message)
        vm.consumeMessage()
        SnapshotMapper.replaceAll(env.db, BackupDataV1())

        vm.prepareRestore(Uri.fromFile(out))
        val pending = vm.state.awaitUntil { it.pending != null }.pending!!
        assertEquals(env.time.nowMs, pending.createdAtMs)
        assertFalse(pending.needsPassword)
        assertEquals(SnapshotMapper.export(env.db), BackupDataV1()) // nothing replaced before confirming
        vm.confirmRestore(null)
        assertEquals(BackupMessage.RESTORED, vm.state.awaitUntil { it.message != null }.message)
        assertEquals(null, vm.state.value.pending)
        assertEquals(before, SnapshotMapper.export(env.db))
    }

    @Test
    fun restore_otherPassword_asksForIt_andFlagsAWrongOne() = runBlocking {
        keyStore.setPassword("senha123".toCharArray(), TEST_ITERATIONS)
        val uri = backupFile()
        keyStore.setPassword("outra-senha".toCharArray(), TEST_ITERATIONS)
        val vm = vm()
        vm.state.awaitUntil { !it.loading }
        vm.prepareRestore(uri)
        assertTrue(vm.state.awaitUntil { it.pending != null }.pending!!.needsPassword)
        vm.confirmRestore("errada".toCharArray())
        assertTrue(vm.state.awaitUntil { it.pending?.passwordError == true }.pending!!.passwordError)
        vm.cancelRestore()
        assertEquals(null, vm.state.awaitUntil { it.pending == null }.pending)
    }

    @Test
    fun restore_ofAFileThatIsNotABackup_reportsCorrupted() = runBlocking {
        val junk = File(dir, "notas.txt").apply { writeText("not a MyFinance backup") }
        val vm = vm()
        vm.state.awaitUntil { !it.loading }
        vm.prepareRestore(Uri.fromFile(junk))
        assertEquals(BackupMessage.CORRUPTED, vm.state.awaitUntil { it.message != null }.message)
        assertEquals(null, vm.state.value.pending)
    }

    @Test
    fun chooseFolder_listsExistingBackups() = runBlocking {
        keyStore.setPassword("senha123".toCharArray(), TEST_ITERATIONS)
        val folder = Files.createTempDirectory("folder").toFile()
        FileBackupDestination(folder).write("myfinance-backup-20261101-120000.myfinance") { manager.create(it) }
        File(folder, "outra-coisa.txt").writeText("ignored")
        val vm = vm()
        vm.state.awaitUntil { !it.loading }
        vm.chooseFolder(Uri.parse(folder.path))
        val state = vm.state.awaitUntil { it.files.isNotEmpty() && it.backup.folderUri != null }
        assertEquals("myfinance-backup-20261101-120000.myfinance", state.files.single().name)
        assertEquals(folder.path, state.backup.folderUri)
    }

    @Test
    fun revokedFolder_flagsFolderError() = runBlocking {
        store.setFolder(File(dir, "gone").path, "Gone")
        val vm = vm { throw BackupError.FolderUnavailable("revoked") }
        assertTrue(vm.state.awaitUntil { it.folderError }.files.isEmpty())
    }

    @Test
    fun autoBackupSwitch_updatesSettings() = runBlocking {
        val vm = vm()
        vm.state.awaitUntil { !it.loading && it.autoBackup }
        vm.setAutoBackup(false)
        vm.state.awaitUntil { !it.autoBackup }
        assertFalse(env.settings.current().autoBackup)
    }
}
