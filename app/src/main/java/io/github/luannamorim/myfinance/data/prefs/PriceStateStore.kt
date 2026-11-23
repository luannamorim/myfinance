package io.github.luannamorim.myfinance.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

enum class PriceError { NETWORK, FORMAT, NOT_FOUND }

/** When stock quotes were last fetched successfully and the last error (not part of backups). */
data class PriceState(val lastFetchMs: Long? = null, val lastAttemptMs: Long? = null, val lastError: PriceError? = null)

@Singleton
class PriceStateStore @Inject constructor(
    @Named("price_state") private val store: DataStore<Preferences>,
) {
    private object Keys {
        val lastFetch = longPreferencesKey("last_fetch_ms")
        val lastAttempt = longPreferencesKey("last_attempt_ms")
        val lastError = stringPreferencesKey("last_error")
    }

    val state: Flow<PriceState> = store.data.map { p ->
        PriceState(p[Keys.lastFetch], p[Keys.lastAttempt], p[Keys.lastError]?.let { enumValueOrNull<PriceError>(it) })
    }

    suspend fun current(): PriceState = state.first()

    /** Always sets lastAttempt; sets lastFetch when [fetched]; stores or clears [error]. */
    suspend fun record(nowMs: Long, fetched: Boolean, error: PriceError?) {
        store.edit {
            it[Keys.lastAttempt] = nowMs
            if (fetched) it[Keys.lastFetch] = nowMs
            if (error != null) it[Keys.lastError] = error.name else it.remove(Keys.lastError)
        }
    }
}
