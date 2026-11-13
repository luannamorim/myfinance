package io.github.luannamorim.myfinance.domain.backup

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** A backup file inside the user's backup folder. [id] is the provider URI (or path in tests). */
data class BackupFileInfo(val id: String, val name: String, val sizeBytes: Long, val createdAtMs: Long)

object Retention {
    const val KEEP = 7
    const val EXTENSION = ".myfinance"
    val NAME_REGEX = Regex("""^myfinance-backup-(\d{8}-\d{6})\.myfinance$""")
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** "myfinance-backup-20261112-143205.myfinance" (local time; sortable by name). */
    fun fileName(createdAtMs: Long, zone: ZoneId): String =
        "myfinance-backup-${Instant.ofEpochMilli(createdAtMs).atZone(zone).toLocalDateTime().format(stamp)}$EXTENSION"

    fun isBackupName(name: String): Boolean = NAME_REGEX.matches(name)

    fun parseCreatedAt(name: String, zone: ZoneId): Long? {
        val match = NAME_REGEX.matchEntire(name) ?: return null
        return try {
            LocalDateTime.parse(match.groupValues[1], stamp).atZone(zone).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** Files to delete so that only the newest [keep] MyFinance backups remain; other files are ignored. */
    fun toDelete(files: List<BackupFileInfo>, keep: Int = KEEP): List<BackupFileInfo> =
        files.filter { isBackupName(it.name) }.sortedByDescending { it.name }.drop(keep)
}
