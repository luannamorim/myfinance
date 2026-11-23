package io.github.luannamorim.myfinance.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PriceStateStoreTest {

    private fun store() = PriceStateStore(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            File(Files.createTempDirectory("prices").toFile(), "p.preferences_pb")
        },
    )

    @Test
    fun record_tracksAttemptFetchAndError() = runBlocking {
        val s = store()
        assertEquals(PriceState(), s.current())
        s.record(1, true, null)
        s.record(2, false, PriceError.NETWORK)
        assertEquals(PriceState(1, 2, PriceError.NETWORK), s.current())
        s.record(3, true, null)
        assertEquals(PriceState(3, 3, null), s.current())
    }
}
