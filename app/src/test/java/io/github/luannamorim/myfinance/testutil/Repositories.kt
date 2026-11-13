package io.github.luannamorim.myfinance.testutil

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.data.db.MyFinanceDatabase
import io.github.luannamorim.myfinance.data.prefs.RateStateStore
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.data.rates.RateClient
import io.github.luannamorim.myfinance.data.repository.BackupTrigger
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
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
