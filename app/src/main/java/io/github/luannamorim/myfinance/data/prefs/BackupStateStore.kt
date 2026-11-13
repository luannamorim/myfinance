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

enum class BackupFailure { NO_FOLDER, FOLDER_UNAVAILABLE, NO_PASSWORD, NO_SPACE, UNKNOWN }

data class BackupState(
    val folderUri: String? = null,
    val folderName: String? = null,
    /** Incremented on every important data change. */
    val changeCount: Long = 0,
    /** changeCount covered by the last successful backup. */
    val savedChangeCount: Long = 0,
    val lastSuccessMs: Long? = null,
    val lastFileName: String? = null,
    val lastAttemptMs: Long? = null,
    val lastError: BackupFailure? = null,
) {
    val hasUnsavedChanges: Boolean get() = changeCount > savedChangeCount
}

@Singleton
class BackupStateStore @Inject constructor(
    @Named("backup_state") private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val folderUri = stringPreferencesKey("folder_uri")
        val folderName = stringPreferencesKey("folder_name")
        val changeCount = longPreferencesKey("change_count")
        val savedChangeCount = longPreferencesKey("saved_change_count")
        val lastSuccess = longPreferencesKey("last_success_ms")
        val lastFile = stringPreferencesKey("last_file")
        val lastAttempt = longPreferencesKey("last_attempt_ms")
        val lastError = stringPreferencesKey("last_error")
    }

    val state: Flow<BackupState> = dataStore.data.map { p ->
        BackupState(
            folderUri = p[Keys.folderUri],
            folderName = p[Keys.folderName],
            changeCount = p[Keys.changeCount] ?: 0,
            savedChangeCount = p[Keys.savedChangeCount] ?: 0,
            lastSuccessMs = p[Keys.lastSuccess],
            lastFileName = p[Keys.lastFile],
            lastAttemptMs = p[Keys.lastAttempt],
            lastError = p[Keys.lastError]?.let { enumValueOrNull<BackupFailure>(it) },
        )
    }

    suspend fun current(): BackupState = state.first()

    suspend fun setFolder(uri: String, name: String?) = dataStore.edit {
        it[Keys.folderUri] = uri
        if (name != null) it[Keys.folderName] = name else it.remove(Keys.folderName)
        it.remove(Keys.lastError)
        // A new folder has no copy yet: make the next run write one.
        it[Keys.changeCount] = (it[Keys.changeCount] ?: 0) + 1
    }

    suspend fun markChanged() = dataStore.edit { it[Keys.changeCount] = (it[Keys.changeCount] ?: 0) + 1 }

    suspend fun recordAttempt(nowMs: Long) = dataStore.edit { it[Keys.lastAttempt] = nowMs }

    /** Marks changes up to [coveredChangeCount] as saved (later changes stay pending). */
    suspend fun recordSuccess(nowMs: Long, fileName: String, coveredChangeCount: Long) = dataStore.edit {
        it[Keys.lastSuccess] = nowMs
        it[Keys.lastFile] = fileName
        it[Keys.savedChangeCount] = maxOf(it[Keys.savedChangeCount] ?: 0, coveredChangeCount)
        it.remove(Keys.lastError)
    }

    suspend fun recordFailure(failure: BackupFailure) = dataStore.edit { it[Keys.lastError] = failure.name }
}
