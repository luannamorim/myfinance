package io.github.luannamorim.myfinance.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.luannamorim.myfinance.domain.model.AppSettings
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.DateFormatStyle
import io.github.luannamorim.myfinance.domain.model.NumberFormatStyle
import io.github.luannamorim.myfinance.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** User settings in DataStore. Exported to and imported from the backup's settings.json. */
@Singleton
class SettingsRepository @Inject constructor(
    @Named("settings") private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val numberFormat = stringPreferencesKey("number_format")
        val dateFormat = stringPreferencesKey("date_format")
        val defaultCurrency = stringPreferencesKey("default_currency")
        val displayCurrency = stringPreferencesKey("display_currency")
        val autoFetchRates = booleanPreferencesKey("auto_fetch_rates")
        val autoBackup = booleanPreferencesKey("auto_backup")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settings: Flow<AppSettings> = dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.theme] = next.themeMode.name
            prefs[Keys.numberFormat] = next.numberFormat.name
            prefs[Keys.dateFormat] = next.dateFormat.name
            prefs[Keys.defaultCurrency] = next.defaultCurrency.name
            prefs[Keys.displayCurrency] = next.displayCurrency.name
            prefs[Keys.autoFetchRates] = next.autoFetchRates
            prefs[Keys.autoBackup] = next.autoBackup
        }
    }

    suspend fun exportJson(): String = json.encodeToString(AppSettings.serializer(), current())

    suspend fun importJson(text: String) {
        val imported = json.decodeFromString(AppSettings.serializer(), text)
        update { imported }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = this[Keys.theme]?.let { enumValueOrNull<ThemeMode>(it) } ?: defaults.themeMode,
            numberFormat = this[Keys.numberFormat]?.let { enumValueOrNull<NumberFormatStyle>(it) } ?: defaults.numberFormat,
            dateFormat = this[Keys.dateFormat]?.let { enumValueOrNull<DateFormatStyle>(it) } ?: defaults.dateFormat,
            defaultCurrency = this[Keys.defaultCurrency]?.let { enumValueOrNull<Currency>(it) } ?: defaults.defaultCurrency,
            displayCurrency = this[Keys.displayCurrency]?.let { enumValueOrNull<Currency>(it) } ?: defaults.displayCurrency,
            autoFetchRates = this[Keys.autoFetchRates] ?: defaults.autoFetchRates,
            autoBackup = this[Keys.autoBackup] ?: defaults.autoBackup,
        )
    }
}

inline fun <reified E : Enum<E>> enumValueOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }
