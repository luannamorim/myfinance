package io.github.luannamorim.myfinance.data.backup

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.domain.backup.BackupError
import io.github.luannamorim.myfinance.domain.backup.BackupFileInfo
import io.github.luannamorim.myfinance.testutil.BackupEnv
import io.github.luannamorim.myfinance.testutil.TEST_ITERATIONS
import io.github.luannamorim.myfinance.testutil.populateEverything
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
/** Plain Application: keeps MyFinanceApp's startup work (restore cleanup, image orphans) out of these tests. */
@Config(sdk = [36], application = Application::class)
class FolderBackupWriterTest {
    private val env = BackupEnv()
    private val keyStore = env.keyStore
    private val manager = env.manager()
    private val writer = FolderBackupWriter(manager, env.time)
    private val dir: File = Files.createTempDirectory("folder").toFile()
    private val destination = FileBackupDestination(dir, env.time.zone())

    @After fun tearDown() = env.close()

    private fun oldBackups(month: String = "202601", count: Int = 9) =
        (1..count).forEach { File(dir, "myfinance-backup-$month${"%02d".format(it)}-120000.myfinance").writeText("old") }

    private fun backups() = dir.list()!!.filter { it.endsWith(".myfinance") }.sorted()

    @Test
    fun run_writesVerifiesThenPrunesToSeven() = runBlocking {
        keyStore.setPassword("senha".toCharArray(), TEST_ITERATIONS)
        env.populateEverything()
        oldBackups()
        File(dir, "outra-coisa.txt").writeText("keep me")
        val info = writer.run(destination)
        val names = dir.list()!!.sorted()
        assertEquals(8, names.size) // 7 backups + the foreign file
        assertTrue(info.name in names)
        assertTrue("outra-coisa.txt" in names)
        assertTrue(names.none { it.endsWith(".partial") })
        assertTrue(manager.verify({ File(info.id).inputStream() }, info.sizeBytes))
    }

    @Test
    fun run_neverPrunesTheNewFile_evenWhenItSortsOldest() = runBlocking {
        keyStore.setPassword("senha".toCharArray(), TEST_ITERATIONS)
        oldBackups(month = "202901") // the clock was set back: every existing name is "newer"
        val info = writer.run(destination)
        val names = backups()
        assertEquals(7, names.size)
        assertEquals(info.name, names.first())
    }

    @Test
    fun run_verificationFails_keepsOlderBackups() = runBlocking {
        keyStore.setPassword("senha".toCharArray(), TEST_ITERATIONS)
        oldBackups()
        val broken = object : BackupDestination by destination {
            // The provider hands back something else than what was written.
            override suspend fun open(file: BackupFileInfo): InputStream {
                val bytes = File(file.id).readBytes()
                return bytes.copyOf(bytes.size - 64).inputStream()
            }
        }
        try {
            writer.run(broken)
            fail("expected Corrupted")
        } catch (e: BackupError.Corrupted) {
        }
        assertEquals(10, backups().size)
    }

    @Test
    fun run_garbageHeader_keepsOlderBackups() = runBlocking {
        keyStore.setPassword("senha".toCharArray(), TEST_ITERATIONS)
        oldBackups()
        val broken = object : BackupDestination by destination {
            override suspend fun open(file: BackupFileInfo): InputStream = "garbage-not-a-backup-header-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx".byteInputStream()
        }
        try {
            writer.run(broken)
            fail("expected Corrupted")
        } catch (e: BackupError.Corrupted) {
        }
        assertEquals(10, backups().size)
    }

    @Test
    fun run_withoutPassword_throwsNoPassword_andLeavesNoFiles() = runBlocking {
        try {
            writer.run(destination)
            fail("expected NoPassword")
        } catch (e: BackupError.NoPassword) {
        }
        assertEquals(0, dir.list()!!.size)
    }
}
