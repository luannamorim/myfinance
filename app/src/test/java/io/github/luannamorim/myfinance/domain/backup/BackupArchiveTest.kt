package io.github.luannamorim.myfinance.domain.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class BackupArchiveTest {
    @get:Rule val tmp = TemporaryFolder()

    private val data = BackupDataV1(
        categories = listOf(CategoryDto(1, null, "EXPENSE", "key", "expense.rent", false, 0, 0)),
        assetCategories = listOf(AssetCategoryDto(1, null, "PERSONAL", "directions_car", "asset.vehicles", false, 0, 0)),
        assets = listOf(
            AssetDto(1, "Carro", 1, "BRL", acquisitionDate = 20_400, imagePath = "images/assets/a.jpg", createdAtMs = 0, updatedAtMs = 0),
            AssetDto(2, "Moto", 1, "BRL", acquisitionDate = 20_401, imagePath = "images/assets/b.jpg", createdAtMs = 0, updatedAtMs = 0),
        ),
        exchangeRates = listOf(ExchangeRateDto(1, "USD", "BRL", 20_404, 5_614_300, "MANUAL", 0)),
    )
    private val meta = ManifestMeta("1.0.0", 10, 1_794_500_000_000, 1)

    private fun image(name: String, size: Int = 5_000) = tmp.newFile(name).apply { writeBytes(Random(name.hashCode()).nextBytes(size)) }

    private fun archive(images: Map<String, File> = mapOf("images/assets/a.jpg" to image("a.jpg"), "images/assets/b.jpg" to image("b.jpg"))): File {
        val file = tmp.newFile("backup.zip")
        file.outputStream().use { BackupArchive.write(it, data, """{"themeMode":"DARK"}""", images, meta) }
        return file
    }

    private fun staging() = File(tmp.root, "staging")

    private inline fun <reified T : BackupError> assertFails(block: () -> Unit) {
        try {
            block(); fail("expected ${T::class.simpleName}")
        } catch (e: BackupError) {
            assertTrue("got $e", e is T)
        }
    }

    /** Rewrites a zip, letting [transform] change or drop entries and [extra] add new ones. */
    private fun rewrite(source: File, transform: (String, ByteArray) -> Pair<String, ByteArray>?, extra: List<Pair<String, ByteArray>> = emptyList()): File {
        val out = tmp.newFile("rewritten-${Random.nextInt()}.zip")
        ZipFile(source).use { zip ->
            ZipOutputStream(out.outputStream()).use { zos ->
                zip.entries().asSequence().forEach { e ->
                    val bytes = zip.getInputStream(e).readBytes()
                    transform(e.name, bytes)?.let { (name, b) -> zos.putNextEntry(ZipEntry(name)); zos.write(b); zos.closeEntry() }
                }
                extra.forEach { (name, b) -> zos.putNextEntry(ZipEntry(name)); zos.write(b); zos.closeEntry() }
            }
        }
        return out
    }

    @Test
    fun writeRead_roundTrip_withImages() {
        val a = image("x.jpg", 70_000)
        val contents = BackupArchive.read(archive(mapOf("images/assets/a.jpg" to a, "images/assets/b.jpg" to image("b.jpg"))), staging())
        assertEquals(data, contents.data)
        assertEquals("""{"themeMode":"DARK"}""", contents.settingsJson)
        assertEquals(data.counts(), contents.manifest.counts)
        assertArrayEquals(a.readBytes(), File(staging(), "images/assets/a.jpg").readBytes())
        assertEquals(1, contents.manifest.formatVersion)
        assertEquals(1, contents.manifest.dbSchemaVersion)
    }

    @Test
    fun counts_haveOneKeyPerTable() {
        assertEquals(
            setOf("categories", "templates", "occurrences", "adjustments", "assetCategories", "assets", "assetFlows", "valuations", "exchangeRates"),
            BackupDataV1().counts().keys,
        )
        assertEquals(setOf("images/assets/a.jpg", "images/assets/b.jpg"), data.referencedImages())
    }

    @Test
    fun hashMismatch_rejected() = assertFails<BackupError.Corrupted> {
        val bad = rewrite(archive(), { name, b -> if (name == "images/assets/b.jpg") name to b.copyOf().also { it[10] = (it[10] + 1).toByte() } else name to b })
        BackupArchive.read(bad, staging())
    }

    @Test
    fun tamperedData_rejected() = assertFails<BackupError.Corrupted> {
        val bad = rewrite(archive(), { name, b -> if (name == BackupArchive.DATA) name to b.decodeToString().replace("Carro", "Barco").toByteArray() else name to b })
        BackupArchive.read(bad, staging())
    }

    @Test
    fun zipSlipEntry_rejected() {
        assertFalse(BackupArchive.isSafeImagePath("images/../../evil.jpg"))
        assertFalse(BackupArchive.isSafeImagePath("/images/assets/a.jpg"))
        assertFalse(BackupArchive.isSafeImagePath("images/assets/sub/a.jpg"))
        assertFalse(BackupArchive.isSafeImagePath("images\\assets\\a.jpg"))
        assertTrue(BackupArchive.isSafeImagePath("images/assets/a.jpg"))
        assertFails<BackupError.Corrupted> {
            BackupArchive.read(rewrite(archive(), { n, b -> n to b }, listOf("images/../evil.jpg" to byteArrayOf(1))), staging())
        }
        assertFalse(File(tmp.root, "evil.jpg").exists())
    }

    @Test
    fun unsafeImagePathInData_rejected() = assertFails<BackupError.Corrupted> {
        val evil = data.copy(assets = listOf(data.assets[0].copy(imagePath = "images/../../databases/myfinance.db")))
        val file = tmp.newFile("evil.zip")
        file.outputStream().use { BackupArchive.write(it, evil, "{}", emptyMap(), meta) }
        BackupArchive.read(file, staging())
    }

    @Test
    fun missingManifest_rejected() = assertFails<BackupError.Corrupted> {
        BackupArchive.read(rewrite(archive(), { n, b -> if (n == BackupArchive.MANIFEST) null else n to b }), staging())
    }

    @Test
    fun missingData_rejected() = assertFails<BackupError.Corrupted> {
        BackupArchive.read(rewrite(archive(), { n, b -> if (n == BackupArchive.DATA) null else n to b }), staging())
    }

    @Test
    fun newerFormatVersion_rejected() = assertFails<BackupError.UnsupportedVersion> {
        BackupArchive.read(
            rewrite(archive(), { n, b -> if (n == BackupArchive.MANIFEST) n to b.decodeToString().replace("\"formatVersion\":1", "\"formatVersion\":2").toByteArray() else n to b }),
            staging(),
        )
    }

    @Test
    fun countsNotMatchingManifest_rejected() = assertFails<BackupError.Corrupted> {
        BackupArchive.read(
            rewrite(archive(), { n, b -> if (n == BackupArchive.MANIFEST) n to b.decodeToString().replace("\"assets\":2", "\"assets\":3").toByteArray() else n to b }),
            staging(),
        )
    }

    @Test
    fun entryNotInManifest_rejected() = assertFails<BackupError.Corrupted> {
        BackupArchive.read(rewrite(archive(), { n, b -> n to b }, listOf("images/assets/extra.jpg" to byteArrayOf(1, 2))), staging())
    }

    @Test
    fun notAZip_rejected() = assertFails<BackupError.Corrupted> {
        BackupArchive.read(tmp.newFile("junk.zip").apply { writeBytes(Random.nextBytes(1000)) }, staging())
    }
}
