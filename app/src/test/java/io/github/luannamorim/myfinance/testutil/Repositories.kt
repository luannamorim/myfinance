package io.github.luannamorim.myfinance.testutil

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.data.db.MyFinanceDatabase
import io.github.luannamorim.myfinance.data.market.QuoteClient
import io.github.luannamorim.myfinance.data.prefs.PriceStateStore
import io.github.luannamorim.myfinance.data.prefs.RateStateStore
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.data.rates.RateClient
import io.github.luannamorim.myfinance.data.repository.BackupTrigger
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.data.repository.PriceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.nio.file.Files

/** Exchange rates backed by [db], with a client that never reaches the network (no rates unless set by hand). */
fun testRates(db: MyFinanceDatabase, settings: SettingsRepository, time: TimeSource): ExchangeRateRepository = ExchangeRateRepository(
    db,
    RateClient { _, _ -> "[]" },
    RateStateStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { File(Files.createTempDirectory("rates").toFile(), "r.preferences_pb") }),
    settings,
    time,
    BackupTrigger { },
)

/** A fresh, empty price fetch state. */
fun testPriceState(): PriceStateStore = PriceStateStore(
    PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { File(Files.createTempDirectory("prices").toFile(), "p.preferences_pb") },
)

/** Stock quotes backed by [db]; the default client never reaches the network (every symbol is unknown). */
fun testPrices(
    db: MyFinanceDatabase,
    settings: SettingsRepository,
    time: TimeSource,
    client: QuoteClient = FakeQuoteClient(),
    backup: BackupTrigger = BackupTrigger { },
): PriceRepository = PriceRepository(db, client, testPriceState(), settings, time, backup)
