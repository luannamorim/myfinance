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

enum class RateError { NETWORK, FORMAT }

/** When rates were last fetched successfully and the last error (not part of backups). */
data class RateState(val lastFetchMs: Long? = null, val lastAttemptMs: Long? = null, val lastError: RateError? = null)

@Singleton
class RateStateStore @Inject constructor(
    @Named("rate_state") private val store: DataStore<Preferences>,
) {
    private object Keys {
        val lastFetch = longPreferencesKey("last_fetch_ms")
        val lastAttempt = longPreferencesKey("last_attempt_ms")
        val lastError = stringPreferencesKey("last_error")
    }

    val state: Flow<RateState> = store.data.map { p ->
        RateState(p[Keys.lastFetch], p[Keys.lastAttempt], p[Keys.lastError]?.let { enumValueOrNull<RateError>(it) })
    }

    suspend fun current(): RateState = state.first()

    suspend fun recordSuccess(nowMs: Long) {
        store.edit {
            it[Keys.lastFetch] = nowMs
            it[Keys.lastAttempt] = nowMs
            it.remove(Keys.lastError)
        }
    }

    suspend fun recordFailure(nowMs: Long, error: RateError) {
        store.edit {
            it[Keys.lastAttempt] = nowMs
            it[Keys.lastError] = error.name
        }
    }
}
