package io.github.luannamorim.myfinance.data.backup

import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.domain.backup.BackupError
import io.github.luannamorim.myfinance.domain.backup.BackupFileInfo
import io.github.luannamorim.myfinance.domain.backup.Retention
import javax.inject.Inject

/** Writes a new backup into the folder, fully verifies it and only then prunes old ones (keeps 7). */
class FolderBackupWriter @Inject constructor(
    private val manager: BackupManager,
    private val time: TimeSource,
) {
    /** Throws [BackupError.Corrupted] when the new file does not verify; older backups are then left alone. */
    suspend fun run(destination: BackupDestination): BackupFileInfo {
        val name = Retention.fileName(time.nowMs(), time.zone())
        val info = destination.write(name) { out -> manager.create(out) }
        // Read the whole file back (decrypt + archive validation) before deleting anything older.
        val valid = destination.open(info).use { stream -> manager.verify({ stream }, info.sizeBytes) }
        if (!valid) throw BackupError.Corrupted("written backup did not verify")
        // Never prune the file just written (a clock set back would sort it as the oldest).
        Retention.toDelete(destination.list().filterNot { it.name == info.name }, Retention.KEEP - 1).forEach { destination.delete(it) }
        return info
    }
}
