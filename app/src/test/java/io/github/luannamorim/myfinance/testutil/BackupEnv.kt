package io.github.luannamorim.myfinance.testutil

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.data.backup.BackupManager
import io.github.luannamorim.myfinance.data.images.ImageStore
import io.github.luannamorim.myfinance.data.prefs.RateStateStore
import io.github.luannamorim.myfinance.data.repository.AssetDraft
import io.github.luannamorim.myfinance.data.repository.BackupTrigger
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.data.repository.PortfolioRepository
import io.github.luannamorim.myfinance.data.repository.TransactionDraft
import io.github.luannamorim.myfinance.domain.cash.RecurrenceRule
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.RecurrenceUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files

/**
 * Real repositories over an in-memory database (seeded categories), a fake clock (2026-11-12 10:00, São Paulo),
 * temp DataStores and a fake PTAX client, for backup tests.
 */
class BackupEnv {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db = inMemoryDb(seed = true)
    val time = FakeTimeSource()
    val settings = testSettings()
    val images = ImageStore(context, Dispatchers.Unconfined)
    val keyStore = testKeyStore()
    val categories = CategoryRepository(db, time, BackupTrigger { })
    val cash = CashRepository(db, time, BackupTrigger { })
    val portfolio = PortfolioRepository(db, time, BackupTrigger { }, images)
    val rates = ExchangeRateRepository(
        db,
        { _, _ -> """[{"data":"10/11/2026","valor":"5.5840"},{"data":"11/11/2026","valor":"5.6012"},{"data":"12/11/2026","valor":"5.6143"}]""" },
        RateStateStore(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
                File(Files.createTempDirectory("rates").toFile(), "rate_state.preferences_pb")
            },
        ),
        settings,
        time,
        BackupTrigger { },
    )

    fun manager(trigger: BackupTrigger = BackupTrigger { }) =
        BackupManager(context, db, settings, keyStore, images, cash, trigger, time, Dispatchers.Unconfined)

    suspend fun seedCategory(key: String): Long = db.categoryDao().getAll().first { it.seedKey == key }.id

    suspend fun seedAssetCategory(key: String): Long = db.categoryDao().getAllAssetCategories().first { it.seedKey == key }.id

    fun close() = db.close()
}

/**
 * Fills all 9 tables through the real repositories: custom and archived (still unnamed) seed categories, both
 * openings and an adjustment, an active and an inactive recurring template with confirmed, cancelled and edited
 * occurrences, one-time transactions in BRL and USD, assets in both currencies with every kind of flow and
 * valuation (one with [assetImage]), a personal asset, and BCB plus MANUAL exchange rates.
 */
suspend fun BackupEnv.populateEverything(assetImage: String? = null) {
    // Categories: a custom one of each kind; a predefined one archived without ever being renamed (name stays NULL).
    val consulting = categories.create(EntryKind.INCOME, "Consultoria", "work")
    val pets = categories.create(EntryKind.EXPENSE, "Pets", "pets")
    categories.setArchived(seedCategory("expense.gym"), true)
    val art = categories.createAssetCategory(AssetGroup.PERSONAL, "Arte", "palette")
    categories.setAssetCategoryArchived(seedAssetCategory("asset.cryptocurrencies"), true)

    // Cash: openings in both currencies and an adjustment with a note.
    cash.setOpening(brl("10000.00"), d("2026-11-01"))
    cash.setOpening(usd("500.00"), d("2026-11-01"))

    // Recurring rent (generated occurrences) plus a fortnightly template that ends and is then deactivated.
    val rent = cash.create(
        TransactionDraft("Aluguel", seedCategory("expense.rent"), EntryKind.EXPENSE, brl("2500.00"), d("2026-11-05"), RecurrenceRule.MONTHLY, description = "Apto 42"),
    )
    val gym = cash.create(
        TransactionDraft("Academia", seedCategory("expense.gym"), EntryKind.EXPENSE, brl("120.00"), d("2026-11-10"), RecurrenceRule(RecurrenceUnit.WEEK, 2), endDate = d("2027-03-01")),
    )
    cash.setTemplateActive(gym, false)
    val rentRows = db.cashDao().getAllOccurrences().filter { it.templateId == rent }.sortedBy { it.dueDate }
    cash.confirm(rentRows[0].id, brl("2550.00"), d("2026-11-06"), "com multa")
    cash.cancel(rentRows[1].id)
    cash.editOccurrence(rentRows[2].id, "Aluguel reajustado", brl("2600.00"), d("2027-01-07"))

    // One-time: settled expense, pending USD income.
    cash.create(TransactionDraft("Ração", pets, EntryKind.EXPENSE, brl("150.00"), d("2026-11-08"), null, alreadySettled = true))
    cash.create(TransactionDraft("Projeto", consulting, EntryKind.INCOME, usd("1200.00"), d("2026-11-20"), null, description = "Fatura 7"))
    cash.addAdjustment(brl("7000.00"), d("2026-11-10"), "conferência com o banco")

    // Portfolio: BRL investment with photo, quantity, contribution and valuations; USD ETF partially sold;
    // an inherited personal asset (initial 0) fully disposed of.
    val selic = portfolio.createAsset(
        AssetDraft(
            name = "Tesouro Selic 2029", categoryId = seedAssetCategory("asset.fixed_income"), currency = Currency.BRL,
            description = "Reserva", institution = "Tesouro Direto", acquisitionDate = d("2026-01-10"),
            quantity = BigDecimal("1.5"), notes = "liquidez diária", imagePath = assetImage,
        ),
        brl("5000.00"),
        brl("5120.00"),
    )
    portfolio.addFlow(selic, FlowKind.CONTRIBUTION, d("2026-06-10"), brl("1000.00"), BigDecimal("0.3"), "aporte")
    portfolio.addValuation(selic, d("2026-10-31"), brl("6300.00"), "extrato")

    val etf = portfolio.createAsset(
        AssetDraft(name = "VT", categoryId = seedAssetCategory("asset.etfs"), currency = Currency.USD, acquisitionDate = d("2026-03-02"), quantity = BigDecimal("10")),
        usd("1000.00"),
        null,
    )
    portfolio.addFlow(etf, FlowKind.PARTIAL_DISPOSAL, d("2026-09-15"), usd("250.00"), BigDecimal("-2"))
    portfolio.addFlow(etf, FlowKind.WITHDRAWAL, d("2026-10-01"), usd("10.00"))
    portfolio.addValuation(etf, d("2026-11-01"), usd("820.00"))

    val painting = portfolio.createAsset(
        AssetDraft(name = "Quadro", categoryId = art, currency = Currency.BRL, acquisitionDate = d("2025-05-01")),
        brl("0.00"),
        brl("3000.00"),
    )
    portfolio.addFlow(painting, FlowKind.FULL_DISPOSAL, d("2026-11-11"), brl("3500.00"))

    // Rates: fetched PTAX (BCB) and a manual rate on a date that also has a BCB one.
    rates.refresh(force = true)
    rates.setManual(d("2026-11-11"), BigDecimal("5.70"))
}
