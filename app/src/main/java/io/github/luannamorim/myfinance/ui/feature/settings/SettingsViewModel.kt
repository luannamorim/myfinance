package io.github.luannamorim.myfinance.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luannamorim.myfinance.BuildConfig
import android.net.Uri
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.data.repository.ExportRepository
import kotlinx.coroutines.CancellationException
import io.github.luannamorim.myfinance.domain.model.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ExportMessage { DONE, ERROR }

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val versionName: String = BuildConfig.VERSION_NAME,
    val exporting: Boolean = false,
    val exportMessage: ExportMessage? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val export: ExportRepository,
) : ViewModel() {

    private val exportState = MutableStateFlow<Pair<Boolean, ExportMessage?>>(false to null)

    val state: StateFlow<SettingsUiState> = combine(settings.settings, exportState) { s, (busy, message) ->
        SettingsUiState(loading = false, settings = s, exporting = busy, exportMessage = message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Writes the CSV zip to the document the user created. */
    fun exportTo(uri: Uri) {
        exportState.value = true to null
        viewModelScope.launch {
            val ok = runCatching { export.exportTo(uri) }.onFailure { if (it is CancellationException) throw it }.isSuccess
            exportState.value = false to if (ok) ExportMessage.DONE else ExportMessage.ERROR
        }
    }

    fun dismissExportMessage() {
        exportState.value = false to null
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }
}
