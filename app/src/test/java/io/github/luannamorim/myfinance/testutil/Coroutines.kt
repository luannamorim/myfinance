package io.github.luannamorim.myfinance.testutil

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.nio.file.Files

/** Runs viewModelScope on an unconfined test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** Waits (real time) for the first value matching [predicate]; for flows fed by Room's own threads. */
suspend fun <T> Flow<T>.awaitUntil(timeoutMs: Long = 5_000, predicate: (T) -> Boolean): T =
    withTimeout(timeoutMs) { first { predicate(it) } }

/** SettingsRepository backed by a temp file and its own scope. */
fun testSettings(): SettingsRepository {
    val dir = Files.createTempDirectory("settings").toFile()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    return SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") })
}
