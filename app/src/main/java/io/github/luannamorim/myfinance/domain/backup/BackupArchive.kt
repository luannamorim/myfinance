package io.github.luannamorim.myfinance.domain.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@Serializable
data class ManifestEntry(val path: String, val size: Long, val sha256: String)

@Serializable
data class BackupManifest(
    val formatVersion: Int = FORMAT_VERSION,
    val appVersionName: String,
    val appVersionCode: Int,
    val createdAtMs: Long,
    val dbSchemaVersion: Int,
    val counts: Map<String, Int>,
    val entries: List<ManifestEntry>,
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

data class ManifestMeta(val appVersionName: String, val appVersionCode: Int, val createdAtMs: Long, val dbSchemaVersion: Int)

data class ArchiveLimits(val maxEntries: Int = 20_000, val maxTotalBytes: Long = 2L * 1024 * 1024 * 1024)

/** Extracted, verified archive. Images live under [stagingDir]/images/... */
data class ArchiveContents(val manifest: BackupManifest, val data: BackupDataV1, val settingsJson: String, val stagingDir: File)

/**
 * Plain (pre-encryption) backup archive: manifest.json + data.json + settings.json + images/.
 * JSON is deflated; images are stored as-is (already compressed).
 */
object BackupArchive {
    const val MANIFEST = "manifest.json"
    const val DATA = "data.json"
    const val SETTINGS = "settings.json"
    const val IMAGES_PREFIX = "images/"

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun write(out: OutputStream, data: BackupDataV1, settingsJson: String, images: Map<String, File>, meta: ManifestMeta) {
        val entries = mutableListOf<ManifestEntry>()
        ZipOutputStream(out).use { zip ->
            fun putBytes(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name).apply { method = ZipEntry.DEFLATED })
                zip.write(bytes)
                zip.closeEntry()
                entries += ManifestEntry(name, bytes.size.toLong(), sha256(bytes))
            }
            putBytes(DATA, json.encodeToString(BackupDataV1.serializer(), data).toByteArray())
            putBytes(SETTINGS, settingsJson.toByteArray())
            images.toSortedMap().forEach { (relative, file) ->
                require(isSafeImagePath(relative)) { "unsafe image path $relative" }
                val crc = CRC32()
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        crc.update(buffer, 0, n)
                        digest.update(buffer, 0, n)
                    }
                }
                zip.putNextEntry(
                    ZipEntry(relative).apply {
                        method = ZipEntry.STORED
                        size = file.length()
                        compressedSize = file.length()
                        this.crc = crc.value
                    },
                )
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                entries += ManifestEntry(relative, file.length(), digest.digest().toHex())
            }
            val manifest = BackupManifest(
                appVersionName = meta.appVersionName,
                appVersionCode = meta.appVersionCode,
                createdAtMs = meta.createdAtMs,
                dbSchemaVersion = meta.dbSchemaVersion,
                counts = data.counts(),
                entries = entries,
            )
            zip.putNextEntry(ZipEntry(MANIFEST).apply { method = ZipEntry.DEFLATED })
            zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
        }
    }

    /** Validates everything (names, limits, hashes, version) and extracts images into [stagingDir]. */
    fun read(zipFile: File, stagingDir: File, limits: ArchiveLimits = ArchiveLimits()): ArchiveContents {
        try {
            ZipFile(zipFile).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toList()
                if (names.size > limits.maxEntries) throw BackupError.Corrupted("too many entries")
                if (names.size != names.toSet().size) throw BackupError.Corrupted("duplicate entries")
                val manifestEntry = zip.getEntry(MANIFEST) ?: throw BackupError.Corrupted("missing manifest")
                val manifest = zip.getInputStream(manifestEntry).use { json.decodeFromString(BackupManifest.serializer(), it.readBytes().decodeToString()) }
                if (manifest.formatVersion > BackupManifest.FORMAT_VERSION) throw BackupError.UnsupportedVersion(manifest.formatVersion)
                if (manifest.entries.sumOf { it.size } > limits.maxTotalBytes) throw BackupError.Corrupted("too large")

                val expected = manifest.entries.associateBy { it.path }
                val payloadNames = names - MANIFEST
                if (payloadNames.toSet() != expected.keys) throw BackupError.Corrupted("entries do not match manifest")
                if (DATA !in expected || SETTINGS !in expected) throw BackupError.Corrupted("missing data")

                stagingDir.deleteRecursively()
                stagingDir.mkdirs()
                var dataBytes: ByteArray? = null
                var settingsBytes: ByteArray? = null
                for (name in payloadNames) {
                    val entry = expected.getValue(name)
                    val zipEntry = zip.getEntry(name)
                    when {
                        name == DATA -> dataBytes = zip.readVerified(zipEntry, entry)
                        name == SETTINGS -> settingsBytes = zip.readVerified(zipEntry, entry)
                        isSafeImagePath(name) -> zip.extractVerified(zipEntry, entry, File(stagingDir, name), stagingDir)
                        else -> throw BackupError.Corrupted("unexpected entry $name")
                    }
                }
                val data = json.decodeFromString(BackupDataV1.serializer(), checkNotNull(dataBytes).decodeToString())
                if (data.counts() != manifest.counts) throw BackupError.Corrupted("counts do not match manifest")
                // Paths stored in the data must stay inside images/ (no traversal into app storage).
                if (data.referencedImages().any { !isSafeImagePath(it) }) throw BackupError.Corrupted("unsafe image path in data")
                return ArchiveContents(manifest, data, checkNotNull(settingsBytes).decodeToString(), stagingDir)
            }
        } catch (e: ZipException) {
            throw BackupError.Corrupted("invalid zip: ${e.message}")
        } catch (e: kotlinx.serialization.SerializationException) {
            throw BackupError.Corrupted("invalid json: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw BackupError.Corrupted(e.message ?: "invalid data")
        }
    }

    /** Only "images/<dir>/<file>" with plain names: no traversal, no absolute paths. */
    fun isSafeImagePath(path: String): Boolean {
        if (!path.startsWith(IMAGES_PREFIX) || path.contains('\\') || path.contains("..")) return false
        val parts = path.split('/')
        return parts.size == 3 && parts.all { it.isNotEmpty() && it != "." }
    }

    private fun ZipFile.readVerified(zipEntry: ZipEntry, expected: ManifestEntry): ByteArray {
        val bytes = getInputStream(zipEntry).use { it.readBytes() }
        if (bytes.size.toLong() != expected.size || sha256(bytes) != expected.sha256) throw BackupError.Corrupted("hash mismatch: ${expected.path}")
        return bytes
    }

    private fun ZipFile.extractVerified(zipEntry: ZipEntry, expected: ManifestEntry, target: File, root: File) {
        if (!target.canonicalPath.startsWith(root.canonicalPath + File.separator)) throw BackupError.Corrupted("path escapes staging")
        target.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        getInputStream(zipEntry).use { raw ->
            DigestInputStream(raw, digest).use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        size += n
                        if (size > expected.size) throw BackupError.Corrupted("entry larger than declared: ${expected.path}")
                        out.write(buffer, 0, n)
                    }
                }
            }
        }
        if (size != expected.size || digest.digest().toHex() != expected.sha256) throw BackupError.Corrupted("hash mismatch: ${expected.path}")
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
