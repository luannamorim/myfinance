package io.github.luannamorim.myfinance.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.domain.backup.BackupArchive
import io.github.luannamorim.myfinance.domain.backup.BackupDataV1
import io.github.luannamorim.myfinance.domain.backup.BackupError
import io.github.luannamorim.myfinance.testutil.BackupEnv
import io.github.luannamorim.myfinance.testutil.inMemoryDb
import io.github.luannamorim.myfinance.testutil.populateEverything
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SnapshotMapperTest {
    private val env = BackupEnv()

    @After fun tearDown() = env.close()

    @Test
    fun exportReplaceAll_roundTripsEveryTable() = runBlocking {
        env.populateEverything(assetImage = "images/assets/p.jpg")
        val exported = SnapshotMapper.export(env.db)
        assertTrue("every table has rows: ${exported.counts()}", exported.counts().values.all { it > 0 })

        // NULL names of predefined categories (archived or not) and MANUAL next to BCB rates survive.
        val gym = exported.categories.single { it.seedKey == "expense.gym" }
        assertNull(gym.name)
        assertTrue(gym.archived)
        assertNull(exported.assetCategories.single { it.seedKey == "asset.cryptocurrencies" }.name)
        val nov11 = java.time.LocalDate.parse("2026-11-11").toEpochDay()
        assertEquals(setOf("BCB", "MANUAL"), exported.exchangeRates.filter { it.date == nov11 }.map { it.source }.toSet())
        assertEquals("1.5", exported.assets.single { it.name == "Tesouro Selic 2029" }.quantity)
        assertEquals("-2", exported.assetFlows.single { it.kind == "PARTIAL_DISPOSAL" }.quantityDelta)
        assertEquals("USD", exported.occurrences.single { it.name == "Projeto" }.currency)
        assertEquals("BRL", exported.occurrences.single { it.state == "CONFIRMED" && it.templateId != null }.actualCurrency)

        val other = inMemoryDb(seed = true) // different content that must be fully replaced
        SnapshotMapper.replaceAll(other, exported)
        assertEquals(exported, SnapshotMapper.export(other))
        assertEquals(setOf("images/assets/p.jpg"), exported.referencedImages())
        other.close()
    }

    @Test
    fun replaceAll_withEmptyData_clearsEveryTable() = runBlocking {
        env.populateEverything()
        SnapshotMapper.replaceAll(env.db, BackupDataV1())
        assertTrue(SnapshotMapper.export(env.db).counts().values.all { it == 0 })
    }

    @Test
    fun json_isStable_andIgnoresUnknownFields() = runBlocking {
        env.populateEverything()
        val exported = SnapshotMapper.export(env.db)
        val text = BackupArchive.json.encodeToString(BackupDataV1.serializer(), exported)
        assertTrue("null names are written explicitly", text.contains("\"name\":null"))
        assertEquals(exported, BackupArchive.json.decodeFromString(BackupDataV1.serializer(), text))
        val withExtra = text.replaceFirst("{", """{"futureField":42,""")
        assertEquals(exported, BackupArchive.json.decodeFromString(BackupDataV1.serializer(), withExtra))
    }

    @Test
    fun unknownEnumValue_isCorrupted_andLeavesDataUntouched() = runBlocking {
        env.populateEverything()
        val before = SnapshotMapper.export(env.db)
        val bad = before.copy(exchangeRates = before.exchangeRates.map { it.copy(source = "ECB") })
        try {
            SnapshotMapper.replaceAll(env.db, bad)
            fail("expected Corrupted")
        } catch (e: BackupError.Corrupted) {
        }
        assertEquals(before, SnapshotMapper.export(env.db))
    }

    @Test
    fun invalidQuantity_isCorrupted() {
        val data = BackupDataV1(assets = listOf(io.github.luannamorim.myfinance.domain.backup.AssetDto(1, "X", 1, "BRL", acquisitionDate = 0, quantity = "1,5", createdAtMs = 0, updatedAtMs = 0)))
        try {
            SnapshotMapper.toRows(data)
            fail("expected Corrupted")
        } catch (e: BackupError.Corrupted) {
        }
    }
}
