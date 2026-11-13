package io.github.luannamorim.myfinance.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.luannamorim.myfinance.domain.backup.BackupError
import io.github.luannamorim.myfinance.domain.backup.BackupFileInfo
import io.github.luannamorim.myfinance.domain.backup.Retention
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.time.ZoneId
import javax.inject.Inject

/** Where backups are written: the folder the user picked (synced to the cloud by another app). */
interface BackupDestination {
    /** MyFinance backups in the folder, any order. */
    suspend fun list(): List<BackupFileInfo>

    /** Writes [name] atomically when the provider supports renames (".partial" first). */
    suspend fun write(name: String, block: suspend (OutputStream) -> Unit): BackupFileInfo

    suspend fun open(file: BackupFileInfo): InputStream

    suspend fun delete(file: BackupFileInfo)
}

fun interface BackupDestinationFactory {
    /** Throws [BackupError.FolderUnavailable] when the folder is gone or access was revoked. */
    fun create(folderUri: String): BackupDestination
}

/** Storage Access Framework folder (persisted tree permission). */
class SafBackupDestination(
    private val context: Context,
    treeUri: Uri,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : BackupDestination {
    private val resolver = context.contentResolver
    private val root: DocumentFile = DocumentFile.fromTreeUri(context, treeUri)
        ?.takeIf { it.exists() && it.isDirectory && it.canWrite() }
        ?: throw BackupError.FolderUnavailable("folder missing or not writable")

    override suspend fun list(): List<BackupFileInfo> = guard {
        root.listFiles().filter { it.isFile && Retention.isBackupName(it.name.orEmpty()) }.map { it.toInfo() }
    }

    override suspend fun write(name: String, block: suspend (OutputStream) -> Unit): BackupFileInfo = guard {
        val partial = root.createFile(MIME, "$name.partial") ?: throw BackupError.FolderUnavailable("cannot create file")
        try {
            (resolver.openOutputStream(partial.uri, "w") ?: throw BackupError.FolderUnavailable("cannot open file")).use { block(it) }
            val final = if (partial.renameTo(name)) {
                partial
            } else {
                // Provider without rename support: copy into the final name, then drop the partial.
                val target = root.createFile(MIME, name) ?: throw BackupError.FolderUnavailable("cannot create file")
                resolver.openInputStream(partial.uri)!!.use { input ->
                    resolver.openOutputStream(target.uri, "w")!!.use { output -> input.copyTo(output) }
                }
                partial.delete()
                target
            }
            final.toInfo()
        } catch (e: Exception) {
            partial.delete()
            throw e
        }
    }

    override suspend fun open(file: BackupFileInfo): InputStream = guard {
        resolver.openInputStream(file.id.toUri()) ?: throw FileNotFoundException("backup file")
    }

    override suspend fun delete(file: BackupFileInfo) {
        guard { DocumentFile.fromSingleUri(context, file.id.toUri())?.delete() }
    }

    private fun DocumentFile.toInfo(): BackupFileInfo {
        val name = name.orEmpty()
        return BackupFileInfo(uri.toString(), name, length(), Retention.parseCreatedAt(name, zone) ?: lastModified())
    }

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: SecurityException) {
        throw BackupError.FolderUnavailable(e.message ?: "permission revoked")
    } catch (e: IllegalArgumentException) {
        throw BackupError.FolderUnavailable(e.message ?: "invalid folder")
    }

    private companion object {
        const val MIME = "application/octet-stream"
    }
}

class SafBackupDestinationFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackupDestinationFactory {
    override fun create(folderUri: String): BackupDestination = SafBackupDestination(context, folderUri.toUri())
}

/** Plain directory destination (tests and debugging). */
class FileBackupDestination(private val dir: File, private val zone: ZoneId = ZoneId.systemDefault()) : BackupDestination {
    override suspend fun list() = dir.listFiles().orEmpty().filter { it.isFile && Retention.isBackupName(it.name) }.map { it.toInfo() }

    override suspend fun write(name: String, block: suspend (OutputStream) -> Unit): BackupFileInfo {
        if (!dir.isDirectory) throw BackupError.FolderUnavailable("missing dir")
        val partial = File(dir, "$name.partial")
        try {
            partial.outputStream().use { block(it) }
            val final = File(dir, name)
            check(partial.renameTo(final)) { "rename failed" }
            return final.toInfo()
        } finally {
            partial.delete()
        }
    }

    override suspend fun open(file: BackupFileInfo): InputStream = File(file.id).inputStream()

    override suspend fun delete(file: BackupFileInfo) {
        File(file.id).delete()
    }

    private fun File.toInfo() = BackupFileInfo(path, name, length(), Retention.parseCreatedAt(name, zone) ?: lastModified())
}
