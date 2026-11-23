package io.github.luannamorim.myfinance.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.domain.backup.BackupArchive
import io.github.luannamorim.myfinance.domain.backup.BackupDataV1
import io.github.luannamorim.myfinance.domain.backup.AssetPriceDto
import io.github.luannamorim.myfinance.domain.backup.AssetSplitDto
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

        assertEquals("AAPL", exported.assets.single { it.name == "Apple" }.ticker)
        assertEquals("231.1234", exported.assetPrices.single().price)
        assertEquals("10", exported.assetSplits.single().numerator)
        assertEquals(2, exported.requiredFormatVersion())

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

    @Test
    fun ticker_roundTrips() = runBlocking {
        val category = env.db.categoryDao().getAllAssetCategories().first().id
        env.db.portfolioDao().insertAsset(io.github.luannamorim.myfinance.testutil.asset(category, "Apple", io.github.luannamorim.myfinance.domain.model.Currency.USD, ticker = "AAPL"))
        val exported = SnapshotMapper.export(env.db)
        assertEquals("AAPL", exported.assets.single { it.name == "Apple" }.ticker)
        assertEquals("AAPL", SnapshotMapper.toRows(exported).assets.single { it.name == "Apple" }.ticker)
    }

    @Test
    fun invalidTicker_rejected() {
        val data = BackupDataV1(assets = listOf(io.github.luannamorim.myfinance.domain.backup.AssetDto(1, "X", 1, "USD", acquisitionDate = 0, createdAtMs = 0, updatedAtMs = 0, ticker = "NVD.DE")))
        try {
            SnapshotMapper.toRows(data)
            fail("expected Corrupted")
        } catch (e: BackupError.Corrupted) {
        }
    }

    @Test
    fun restores141DataJson() {
        val text = """{"categories":[],"templates":[],"occurrences":[],"adjustments":[],"assetCategories":[{"id":1,"name":null,"assetGroup":"PERSONAL","icon":"x","seedKey":"asset.vehicles","archived":false,"position":0,"createdAtMs":0}],
            "assets":[{"id":1,"name":"Carro","categoryId":1,"currency":"BRL","description":null,"institution":null,"acquisitionDate":20400,"quantity":null,"notes":null,"imagePath":null,"createdAtMs":0,"updatedAtMs":0}],
            "assetFlows":[],"valuations":[],"exchangeRates":[]}"""
        val data = BackupArchive.json.decodeFromString(BackupDataV1.serializer(), text)
        assertEquals(9, data.counts().size)
        assertEquals(1, data.requiredFormatVersion())
        assertNull(SnapshotMapper.toRows(data).assets.single().ticker)
    }

    private fun assertCorrupted(data: BackupDataV1) {
        try {
            SnapshotMapper.toRows(data)
            fail("expected Corrupted")
        } catch (e: BackupError.Corrupted) {
        }
    }

    @Test
    fun invalidQuotes_areCorrupted() {
        val price = AssetPriceDto("AAPL", 20_400, "231.12", "YAHOO", 0)
        val split = AssetSplitDto("AAPL", 20_400, "10", "1")
        SnapshotMapper.toRows(BackupDataV1(assetPrices = listOf(price), assetSplits = listOf(split)))
        assertCorrupted(BackupDataV1(assetPrices = listOf(price.copy(symbol = "nvd.de"))))
        assertCorrupted(BackupDataV1(assetPrices = listOf(price.copy(symbol = "aapl"))))
        assertCorrupted(BackupDataV1(assetPrices = listOf(price.copy(price = "abc"))))
        assertCorrupted(BackupDataV1(assetPrices = listOf(price.copy(price = "0"))))
        assertCorrupted(BackupDataV1(assetPrices = listOf(price.copy(price = "-1"))))
        assertCorrupted(BackupDataV1(assetPrices = listOf(price.copy(source = "GOOGLE"))))
        assertCorrupted(BackupDataV1(assetSplits = listOf(split.copy(symbol = "??"))))
        assertCorrupted(BackupDataV1(assetSplits = listOf(split.copy(numerator = "0"))))
        assertCorrupted(BackupDataV1(assetSplits = listOf(split.copy(denominator = "x"))))
        assertCorrupted(BackupDataV1(assetSplits = listOf(split.copy(denominator = "-2"))))
    }
}
