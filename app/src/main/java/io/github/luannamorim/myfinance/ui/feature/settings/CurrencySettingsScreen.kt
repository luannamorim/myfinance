package io.github.luannamorim.myfinance.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.core.format.RateInput
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.prefs.RateError
import io.github.luannamorim.myfinance.data.prefs.RateState
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.data.repository.ExchangeRateRepository
import io.github.luannamorim.myfinance.data.repository.RefreshResult
import io.github.luannamorim.myfinance.domain.model.AppSettings
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.ExchangeRate
import io.github.luannamorim.myfinance.domain.model.RateSource
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.components.ChoiceRow
import io.github.luannamorim.myfinance.ui.components.DateSelector
import io.github.luannamorim.myfinance.ui.components.DetailScaffold
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.FinanceTextField
import io.github.luannamorim.myfinance.ui.components.SecondaryButton
import io.github.luannamorim.myfinance.ui.components.SectionHeader
import io.github.luannamorim.myfinance.ui.components.SwitchRow
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class CurrencyUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val rateState: RateState = RateState(),
    /** Most recent first. */
    val rates: List<ExchangeRate> = emptyList(),
    val updating: Boolean = false,
    val result: RefreshResult? = null,
    val manualDate: LocalDate = LocalDate.MIN,
    val manualText: String = "",
    val manualError: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
)

@HiltViewModel
class CurrencySettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val rates: ExchangeRateRepository,
    time: TimeSource,
) : ViewModel() {

    private val local = MutableStateFlow(CurrencyUiState(manualDate = time.today(), today = time.today()))

    val state: StateFlow<CurrencyUiState> = combine(settings.settings, rates.state, rates.rates, local) { s, rs, r, l ->
        l.copy(loading = false, settings = s, rateState = rs, rates = r.recent(MAX_RATES))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    fun refresh() {
        local.update { it.copy(updating = true, result = null) }
        viewModelScope.launch {
            val result = rates.refresh(force = true)
            local.update { it.copy(updating = false, result = result) }
        }
    }

    fun setManualDate(date: LocalDate) = local.update { it.copy(manualDate = date) }
    fun setManualText(text: String) = local.update { it.copy(manualText = text, manualError = false) }

    fun saveManual() {
        val s = local.value
        val rate = RateInput.parse(s.manualText) ?: return local.update { it.copy(manualError = true) }
        viewModelScope.launch {
            rates.setManual(s.manualDate, rate)
            local.update { it.copy(manualText = "") }
        }
    }

    fun deleteManual(date: LocalDate) = viewModelScope.launch { rates.deleteManual(date) }

    companion object {
        const val MAX_RATES = 30
    }
}

data class CurrencyActions(
    val onBack: () -> Unit = {},
    val onUpdate: ((AppSettings) -> AppSettings) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onManualDate: (LocalDate) -> Unit = {},
    val onManualText: (String) -> Unit = {},
    val onSaveManual: () -> Unit = {},
    val onDeleteManual: (LocalDate) -> Unit = {},
)

@Composable
fun CurrencySettingsScreen(onBack: () -> Unit, viewModel: CurrencySettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CurrencySettingsContent(
        state,
        CurrencyActions(onBack, viewModel::update, viewModel::refresh, viewModel::setManualDate, viewModel::setManualText, viewModel::saveManual, { viewModel.deleteManual(it) }),
    )
}

@Composable
fun CurrencySettingsContent(state: CurrencyUiState, actions: CurrencyActions) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    val s = state.settings
    val currencies = Currency.entries.map { it to it.symbol }
    DetailScaffold(title = stringResource(R.string.currency_title), onBack = actions.onBack) {
        FinanceCard(Modifier.fillMaxWidth()) {
            ChoiceRow(stringResource(R.string.currency_display), currencies, s.displayCurrency, { cur -> actions.onUpdate { it.copy(displayCurrency = cur) } })
            Spacer(Modifier.height(Spacing.l))
            ChoiceRow(stringResource(R.string.currency_default), currencies, s.defaultCurrency, { cur -> actions.onUpdate { it.copy(defaultCurrency = cur) } })
        }
        FinanceCard(Modifier.fillMaxWidth()) {
            SwitchRow(stringResource(R.string.currency_auto), stringResource(R.string.currency_auto_hint), s.autoFetchRates, { v -> actions.onUpdate { it.copy(autoFetchRates = v) } })
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.s)) {
                SecondaryButton(stringResource(R.string.currency_update), onClick = actions.onRefresh, enabled = !state.updating)
                Spacer(Modifier.weight(1f))
            }
            val last = state.rateState.lastFetchMs?.let { ms ->
                val dt = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
                stringResource(R.string.currency_last_fetch, formats.dates.date(dt.toLocalDate()) + " " + "%02d:%02d".format(dt.hour, dt.minute))
            } ?: stringResource(R.string.currency_never)
            Text(last, style = FinanceTheme.type.caption, color = c.textSecondary, modifier = Modifier.padding(top = Spacing.s))
            val message = when (val r = state.result) {
                is RefreshResult.Updated -> stringResource(R.string.currency_updated) to c.positive
                RefreshResult.UpToDate -> stringResource(R.string.currency_up_to_date) to c.textSecondary
                is RefreshResult.Failed -> stringResource(if (r.reason == RateError.NETWORK) R.string.currency_error_network else R.string.currency_error_format) to c.danger
                else -> state.rateState.lastError?.let {
                    stringResource(if (it == RateError.NETWORK) R.string.currency_error_network else R.string.currency_error_format) to c.warning
                }
            }
            message?.let { (text, color) -> Text(text, style = FinanceTheme.type.caption, color = color) }
        }

        SectionHeader(stringResource(R.string.currency_manual), Modifier.padding(top = Spacing.s))
        FinanceCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                DateSelector(stringResource(R.string.editor_date), state.manualDate, actions.onManualDate, maxDate = state.today)
                FinanceTextField(
                    value = state.manualText,
                    onValueChange = actions.onManualText,
                    label = stringResource(R.string.currency_manual_rate),
                    keyboardType = KeyboardType.Decimal,
                    isError = state.manualError,
                    supportingText = if (state.manualError) stringResource(R.string.currency_manual_invalid) else stringResource(R.string.currency_manual_hint),
                )
                SecondaryButton(stringResource(R.string.action_save), onClick = actions.onSaveManual)
            }
        }

        if (state.rates.isNotEmpty()) {
            SectionHeader(stringResource(R.string.currency_rates), Modifier.padding(top = Spacing.s))
            FinanceCard(Modifier.fillMaxWidth(), contentPadding = Spacing.m) {
                state.rates.forEach { rate ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(formats.dates.date(rate.date), style = FinanceTheme.type.body, color = c.textPrimary, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(if (rate.source == RateSource.MANUAL) R.string.rate_source_manual else R.string.rate_source_bcb),
                            style = FinanceTheme.type.caption,
                            color = c.textSecondary,
                            modifier = Modifier.padding(end = Spacing.m),
                        )
                        Text(formats.money.formatRate(rate.rate), style = FinanceTheme.type.numeric, color = c.textPrimary)
                        if (rate.source == RateSource.MANUAL) {
                            IconButton(onClick = { actions.onDeleteManual(rate.date) }) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.action_delete), tint = c.textSecondary) }
                        }
                    }
                }
            }
        }
    }
}
