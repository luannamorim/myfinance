package io.github.luannamorim.myfinance.data.backup

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.luannamorim.myfinance.BuildConfig
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.data.db.MyFinanceDatabase
import io.github.luannamorim.myfinance.data.images.ImageStore
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.data.repository.BackupTrigger
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.di.IoDispatcher
import io.github.luannamorim.myfinance.domain.backup.BackupArchive
import io.github.luannamorim.myfinance.domain.backup.BackupCrypto
import io.github.luannamorim.myfinance.domain.backup.BackupError
import io.github.luannamorim.myfinance.domain.backup.BackupHeader
import io.github.luannamorim.myfinance.domain.backup.KeyDerivation
import io.github.luannamorim.myfinance.domain.backup.ManifestMeta
import io.github.luannamorim.myfinance.domain.model.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class BackupInfo(val createdAtMs: Long, val sizeBytes: Long, val counts: Map<String, Int>)

sealed interface RestoreResult {
    data class Success(val counts: Map<String, Int>) : RestoreResult
    data object WrongPassword : RestoreResult
    /** The file was made with another password than the one stored on this device. */
    data object PasswordRequired : RestoreResult
    data class Corrupted(val reason: String) : RestoreResult
    data object UnsupportedVersion : RestoreResult
    data object InsufficientSpace : RestoreResult
}

/**
 * Creates encrypted backups of everything and restores them atomically: the database is replaced in one
 * transaction, images are merged before the commit (unique names) and stale ones are removed after it, so a
 * crash at any point leaves a consistent state. Never logs data or file names, only error types.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: MyFinanceDatabase,
    private val settings: SettingsRepository,
    private val keyStore: BackupKeyStore,
    private val images: ImageStore,
    private val cash: CashRepository,
    private val backupTrigger: BackupTrigger,
    private val time: TimeSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val stagingDir get() = File(context.filesDir, "restore_staging")
    private val workZip get() = File(context.cacheDir, "restore.zip")

    /** Writes an encrypted backup to [out]. Throws [BackupError.NoPassword] when no key is set. */
    suspend fun create(out: OutputStream): BackupInfo = mutex.withLock {
        withContext(io) {
            val key = keyStore.key() ?: throw BackupError.NoPassword()
            val now = time.nowMs()
            val data = SnapshotMapper.export(db)
            val imageFiles = data.referencedImages().associateWith { images.file(it) }.filterValues { it.isFile }
            val tmp = File(context.cacheDir, "backup-$now.zip")
            try {
                tmp.outputStream().use {
                    BackupArchive.write(
                        it, data, settings.exportJson(), imageFiles,
                        ManifestMeta(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, now, MyFinanceDatabase.VERSION),
                    )
                }
                val header = BackupHeader(iterations = key.iterations, salt = key.salt, keyCheck = key.keyCheck, createdAtMs = now)
                val counting = CountingOutputStream(out)
                tmp.inputStream().use { BackupCrypto.encrypt(key.key, header, it, counting) }
                BackupInfo(now, counting.count, data.counts())
            } finally {
                tmp.delete()
            }
        }
    }

    /** Reads only the header (date shown before confirming a restore). */
    suspend fun inspect(input: InputStream): BackupHeader = withContext(io) { input.use { BackupHeader.read(it) } }

    /**
     * Fully checks a written backup with this device's key: decrypts the whole stream (truncation and tampering
     * are only detected at the end) and validates the archive (manifest, hashes, counts, image paths) in a
     * temporary directory that is always deleted. False when the file is not a valid backup for this key.
     * Throws [IOException] when there is no room to check it.
     */
    suspend fun verify(input: () -> InputStream, sizeBytes: Long): Boolean = withContext(io) {
        val key = keyStore.key() ?: return@withContext false
        if (sizeBytes <= BackupHeader.SIZE) return@withContext false
        @Suppress("UsableSpace") // a rough pre-check is enough
        if (context.cacheDir.usableSpace < sizeBytes * 2) throw IOException("No space left to verify the backup")
        val dir = File(context.cacheDir, "verify-${System.nanoTime()}")
        try {
            dir.mkdirs()
            val zip = File(dir, "backup.zip")
            input().use { stream ->
                val header = BackupHeader.read(stream)
                zip.outputStream().use { BackupCrypto.decrypt(key.key, header, stream, it) }
            }
            BackupArchive.read(zip, File(dir, "staging"))
            true
        } catch (e: BackupError) {
            Log.w(TAG, "backup verification failed: ${e.javaClass.simpleName}")
            false
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * Restores a backup. With [password] == null the key stored on this device is used, which only
     * works for backups made with the same password setup ([RestoreResult.PasswordRequired] otherwise).
     */
    suspend fun restore(input: InputStream, password: CharArray?, sizeBytes: Long? = null): RestoreResult = mutex.withLock {
        withContext(io) {
            try {
                input.use { stream -> restoreLocked(stream, password, sizeBytes) }
            } catch (e: BackupError.WrongPassword) {
                RestoreResult.WrongPassword
            } catch (e: BackupError.UnsupportedVersion) {
                RestoreResult.UnsupportedVersion
            } catch (e: BackupError) {
                Log.w(TAG, "restore rejected: ${e.javaClass.simpleName}")
                RestoreResult.Corrupted(e.message ?: "corrupted")
            } catch (e: android.database.SQLException) {
                Log.w(TAG, "restore rejected: ${e.javaClass.simpleName}")
                RestoreResult.Corrupted("database constraint")
            } catch (e: IOException) {
                Log.w(TAG, "restore failed: ${e.javaClass.simpleName}")
                if (e.isNoSpace()) RestoreResult.InsufficientSpace else RestoreResult.Corrupted("io")
            } finally {
                workZip.delete()
                stagingDir.deleteRecursively()
            }
        }
    }

    private suspend fun restoreLocked(stream: InputStream, password: CharArray?, sizeBytes: Long?): RestoreResult {
        val header = BackupHeader.read(stream)
        val key = if (password != null) {
            KeyDerivation.derive(password, header.salt, header.iterations)
        } else {
            keyStore.key()?.takeIf { it.salt.contentEquals(header.salt) && it.iterations == header.iterations }?.key
                ?: return RestoreResult.PasswordRequired
        }
        if (!BackupCrypto.keyMatches(key, header)) {
            return if (password != null) RestoreResult.WrongPassword else RestoreResult.PasswordRequired
        }
        @Suppress("UsableSpace") // a rough pre-check is enough; failures while writing are handled too
        if (sizeBytes != null && context.cacheDir.usableSpace < sizeBytes * 2) return RestoreResult.InsufficientSpace

        // 1. Decrypt fully (truncation/tampering is only detected at the end) and verify the archive, the rows
        //    (enum names, decimals) and the settings before anything local changes.
        workZip.outputStream().use { BackupCrypto.decrypt(key, header, stream, it) }
        val contents = BackupArchive.read(workZip, stagingDir)
        val rows = SnapshotMapper.toRows(contents.data)
        try {
            BackupArchive.json.decodeFromString(AppSettings.serializer(), contents.settingsJson)
        } catch (e: SerializationException) {
            throw BackupError.Corrupted("invalid settings")
        } catch (e: IllegalArgumentException) {
            throw BackupError.Corrupted("invalid settings")
        }

        try {
            // 2. Merge images before the commit: names are unique, current images stay untouched.
            val imagesRoot = context.filesDir
            File(stagingDir, ImageStore.IMAGES_DIR).walkTopDown().filter { it.isFile }.forEach { file ->
                val target = File(imagesRoot, file.relativeTo(stagingDir).invariantSeparatorsPath)
                target.parentFile?.mkdirs()
                if (!file.renameTo(target)) file.copyTo(target, overwrite = true)
            }

            // 3. Atomic data swap, then settings and cleanup of images nobody references anymore.
            backupTrigger.cancelPending()
            SnapshotMapper.replaceAll(db, rows)
            settings.importJson(contents.settingsJson)
            images.deleteOrphans(contents.data.referencedImages())
        } catch (e: Exception) {
            // Whatever was merged and is not referenced by the data now in the database goes away.
            if (e !is CancellationException) runCatching { images.deleteOrphans(SnapshotMapper.export(db).referencedImages()) }
            throw e
        }

        // 4. Validate the result.
        val restored = SnapshotMapper.export(db)
        if (restored.counts() != contents.manifest.counts) throw BackupError.Corrupted("restored counts differ")
        val missing = contents.manifest.entries.filter { BackupArchive.isSafeImagePath(it.path) && !images.file(it.path).isFile }
        if (missing.isNotEmpty()) throw BackupError.Corrupted("missing images after restore: ${missing.size}")

        // The password that opened this backup becomes this device's backup password. A file's KDF
        // parameters are only adopted when they are at least as strong as ours; otherwise re-derive.
        if (password != null) {
            if (header.iterations >= KeyDerivation.DEFAULT_ITERATIONS) {
                keyStore.adopt(StoredKey(key, header.salt, header.iterations))
            } else {
                keyStore.setPassword(password)
            }
        }

        // 5. Top up the occurrence horizon from today (the backup may be months old). Not fatal: it also runs at start.
        try {
            cash.ensureGenerated()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "horizon regeneration after restore failed: ${e.javaClass.simpleName}")
        }
        return RestoreResult.Success(restored.counts())
    }

    /** Cleans leftovers of a restore interrupted by process death (safe to call at app start). */
    suspend fun recoverInterruptedRestore() = mutex.withLock {
        withContext(io) {
            if (stagingDir.exists() || workZip.exists()) {
                stagingDir.deleteRecursively()
                workZip.delete()
                images.deleteOrphans(SnapshotMapper.export(db).referencedImages())
            }
        }
    }

    private fun IOException.isNoSpace() = message.orEmpty().let { it.contains("ENOSPC") || it.contains("No space", ignoreCase = true) }

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    private companion object {
        const val TAG = "BackupManager"
    }
}
