package io.github.luannamorim.myfinance.data.backup

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.data.images.ImageKind
import io.github.luannamorim.myfinance.data.repository.BackupTrigger
import io.github.luannamorim.myfinance.domain.backup.BackupArchive
import io.github.luannamorim.myfinance.domain.backup.BackupCrypto
import io.github.luannamorim.myfinance.domain.backup.BackupDataV1
import io.github.luannamorim.myfinance.domain.backup.BackupError
import io.github.luannamorim.myfinance.domain.backup.BackupHeader
import io.github.luannamorim.myfinance.domain.backup.KeyDerivation
import io.github.luannamorim.myfinance.domain.backup.ManifestMeta
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.NumberFormatStyle
import io.github.luannamorim.myfinance.domain.model.ThemeMode
import io.github.luannamorim.myfinance.testutil.BackupEnv
import io.github.luannamorim.myfinance.testutil.TEST_ITERATIONS
import io.github.luannamorim.myfinance.testutil.populateEverything
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
/** Plain Application: keeps MyFinanceApp's startup work (restore cleanup, image orphans) out of these tests. */
@Config(sdk = [36], application = Application::class)
class BackupManagerTest {
    private val env = BackupEnv()
    private val images = env.images
    private val keyStore = env.keyStore
    private var cancelled = 0
    private val trigger = object : BackupTrigger {
        override fun onDataChanged() {}
        override fun cancelPending() { cancelled++ }
    }
    private val manager = env.manager(trigger)

    @After fun tearDown() = env.close()

    private suspend fun photo(name: String): String {
        val file = File(env.context.cacheDir, "$name.jpg")
        file.outputStream().use { Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return images.import(Uri.fromFile(file), ImageKind.ASSET)
    }

    private suspend fun backupBytes(): ByteArray {
        keyStore.key() ?: keyStore.setPassword("minha senha".toCharArray(), TEST_ITERATIONS)
        return ByteArrayOutputStream().also { manager.create(it) }.toByteArray()
    }

    /** A backup encrypted with this device's key whose content is [data] (no validation on the way in). */
    private suspend fun crafted(data: BackupDataV1, imageFiles: Map<String, File> = emptyMap()): ByteArray {
        val key = keyStore.key() ?: keyStore.setPassword("minha senha".toCharArray(), TEST_ITERATIONS)
        val zip = File(env.context.cacheDir, "crafted.zip")
        zip.outputStream().use { BackupArchive.write(it, data, env.settings.exportJson(), imageFiles, ManifestMeta("x", 1, env.time.nowMs(), 1)) }
        val header = BackupHeader(iterations = key.iterations, salt = key.salt, keyCheck = key.keyCheck, createdAtMs = env.time.nowMs())
        return ByteArrayOutputStream().also { out -> zip.inputStream().use { BackupCrypto.encrypt(key.key, header, it, out) } }.toByteArray()
    }

    @Test
    fun createRestore_roundTrip_restoresDbImagesAndSettings() = runBlocking {
        val path = photo("p")
        val photoBytes = images.file(path).readBytes()
        env.populateEverything(assetImage = path)
        env.settings.update { it.copy(themeMode = ThemeMode.LIGHT, numberFormat = NumberFormatStyle.DOT_DECIMAL, displayCurrency = Currency.USD, autoBackup = false) }
        val before = SnapshotMapper.export(env.db)
        val backup = backupBytes()

        // Wipe local state.
        SnapshotMapper.replaceAll(env.db, BackupDataV1())
        images.delete(path)
        env.settings.update { it.copy(themeMode = ThemeMode.DARK, numberFormat = NumberFormatStyle.COMMA_DECIMAL, displayCurrency = Currency.BRL, autoBackup = true) }
        val orphan = photo("orphan")

        val result = manager.restore(ByteArrayInputStream(backup), password = null)
        assertTrue("$result", result is RestoreResult.Success)
        assertEquals(before.counts(), (result as RestoreResult.Success).counts)
        assertEquals(before, SnapshotMapper.export(env.db))
        assertArrayEquals(photoBytes, images.file(path).readBytes())
        assertFalse("orphan image removed", images.file(orphan).exists())
        val settings = env.settings.current()
        assertEquals(ThemeMode.LIGHT, settings.themeMode)
        assertEquals(NumberFormatStyle.DOT_DECIMAL, settings.numberFormat)
        assertEquals(Currency.USD, settings.displayCurrency)
        assertFalse(settings.autoBackup)
        assertEquals(1, cancelled)
        assertFalse(File(env.context.filesDir, "restore_staging").exists())
    }

    @Test
    fun restore_regeneratesTheOccurrenceHorizonFromToday() = runBlocking {
        env.populateEverything()
        val backup = backupBytes()
        val rent = SnapshotMapper.export(env.db).templates.single { it.name == "Aluguel" }
        val before = SnapshotMapper.export(env.db).occurrences.count { it.templateId == rent.id }

        env.time.set("2027-04-20") // the backup is restored five months later
        assertTrue(manager.restore(ByteArrayInputStream(backup), null) is RestoreResult.Success)
        val after = SnapshotMapper.export(env.db)
        assertEquals(before + 5, after.occurrences.count { it.templateId == rent.id })
        assertTrue(after.templates.single { it.id == rent.id }.generatedThrough!! > rent.generatedThrough!!)
    }

    @Test
    fun restore_wrongPassword_leavesLocalDataUntouched() = runBlocking {
        env.populateEverything()
        val backup = backupBytes()
        val before = SnapshotMapper.export(env.db)
        assertEquals(RestoreResult.WrongPassword, manager.restore(ByteArrayInputStream(backup), "errada".toCharArray()))
        assertEquals(before, SnapshotMapper.export(env.db))
    }

    @Test
    fun restore_corrupted_leavesLocalDataUntouched() = runBlocking {
        env.populateEverything()
        val backup = backupBytes()
        backup[backup.size / 2] = (backup[backup.size / 2].toInt() xor 0xFF).toByte()
        val before = SnapshotMapper.export(env.db)
        assertTrue(manager.restore(ByteArrayInputStream(backup), null) is RestoreResult.Corrupted)
        assertEquals(before, SnapshotMapper.export(env.db))
        assertFalse(File(env.context.filesDir, "restore_staging").exists())
        assertEquals(0, cancelled)
    }

    @Test
    fun restore_rowsBreakingConstraints_rollsBack_andRemovesMergedImages() = runBlocking {
        val path = photo("kept")
        env.populateEverything(assetImage = path)
        val before = SnapshotMapper.export(env.db)
        val incoming = File(env.context.cacheDir, "incoming.jpg").apply { writeBytes(images.file(path).readBytes()) }
        // An occurrence pointing at a category that does not exist, plus an image that is new to this device.
        val broken = before.copy(
            occurrences = before.occurrences.mapIndexed { i, o -> if (i == 0) o.copy(categoryId = 9_999) else o },
            assets = before.assets.map { if (it.imagePath == path) it.copy(imagePath = "images/assets/new.jpg") else it },
        )
        val backup = crafted(broken, mapOf("images/assets/new.jpg" to incoming))

        assertTrue(manager.restore(ByteArrayInputStream(backup), null) is RestoreResult.Corrupted)
        assertEquals(before, SnapshotMapper.export(env.db))
        assertTrue(images.file(path).isFile)
        assertFalse("merged image of the failed restore removed", images.file("images/assets/new.jpg").exists())
    }

    @Test
    fun restore_unknownEnum_isCorrupted_beforeTouchingAnything() = runBlocking {
        env.populateEverything()
        val before = SnapshotMapper.export(env.db)
        val backup = crafted(before.copy(templates = before.templates.map { it.copy(recurrenceUnit = "FORTNIGHT") }))
        assertTrue(manager.restore(ByteArrayInputStream(backup), null) is RestoreResult.Corrupted)
        assertEquals(before, SnapshotMapper.export(env.db))
        assertEquals(0, cancelled)
    }

    @Test
    fun restore_otherPassword_requiresPassword_thenStoresThatPassword() = runBlocking {
        val backup = backupBytes() // weak test KDF parameters
        keyStore.setPassword("nova senha".toCharArray(), TEST_ITERATIONS) // e.g. a new phone with another setup
        assertEquals(RestoreResult.PasswordRequired, manager.restore(ByteArrayInputStream(backup), null))
        assertTrue(manager.restore(ByteArrayInputStream(backup), "minha senha".toCharArray()) is RestoreResult.Success)
        // Weak parameters from a file are never adopted: the password is re-derived with the defaults.
        val stored = keyStore.key()!!
        assertEquals(KeyDerivation.DEFAULT_ITERATIONS, stored.iterations)
        assertTrue(keyStore.unlock("minha senha".toCharArray()))
    }

    @Test
    fun create_withoutPassword_throwsNoPassword() = runBlocking {
        try {
            manager.create(ByteArrayOutputStream())
            fail("expected NoPassword")
        } catch (e: BackupError.NoPassword) {
        }
    }

    @Test
    fun verify_acceptsAGoodFile_andRejectsTamperedOrForeignOnes() = runBlocking {
        env.populateEverything()
        val good = backupBytes()
        assertTrue(manager.verify({ ByteArrayInputStream(good) }, good.size.toLong()))

        val truncated = good.copyOf(good.size - 100)
        assertFalse(manager.verify({ ByteArrayInputStream(truncated) }, truncated.size.toLong()))
        val flipped = good.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        assertFalse(manager.verify({ ByteArrayInputStream(flipped) }, flipped.size.toLong()))

        keyStore.setPassword("outra senha".toCharArray(), TEST_ITERATIONS)
        assertFalse("made with another key", manager.verify({ ByteArrayInputStream(good) }, good.size.toLong()))
        assertTrue(env.context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("verify-") })
    }

    @Test
    fun recoverInterruptedRestore_cleansLeftovers() = runBlocking {
        val staging = File(env.context.filesDir, "restore_staging/images/assets").apply { mkdirs() }
        File(staging, "x.jpg").writeText("x")
        val orphan = photo("leftover")
        manager.recoverInterruptedRestore()
        assertFalse(File(env.context.filesDir, "restore_staging").exists())
        assertFalse(images.file(orphan).exists())
    }
}
