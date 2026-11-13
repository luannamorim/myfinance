package io.github.luannamorim.myfinance.ui.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.domain.model.AppSettings
import io.github.luannamorim.myfinance.domain.model.DateFormatStyle
import io.github.luannamorim.myfinance.domain.model.NumberFormatStyle
import io.github.luannamorim.myfinance.domain.model.ThemeMode
import io.github.luannamorim.myfinance.ui.components.ChoiceRow
import io.github.luannamorim.myfinance.ui.components.ConfirmDialog
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.NavRow
import io.github.luannamorim.myfinance.ui.components.SectionHeader
import io.github.luannamorim.myfinance.ui.components.TabScreen
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing

data class SettingsActions(
    val onUpdate: ((AppSettings) -> AppSettings) -> Unit = {},
    val onOpenCategories: () -> Unit = {},
    val onOpenOpening: () -> Unit = {},
    val onOpenAdjust: () -> Unit = {},
    val onOpenRecurring: () -> Unit = {},
    val onOpenCurrency: () -> Unit = {},
    val onOpenBackup: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onDismissExport: () -> Unit = {},
    val onOpenPrivacy: () -> Unit = {},
    val onOpenAbout: () -> Unit = {},
)

@Composable
fun SettingsScreen(actions: SettingsActions, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::exportTo)
    }
    val fileName = "myfinance-export-" + java.time.LocalDate.now().toString().replace("-", "") + ".zip"
    SettingsContent(
        state,
        actions.copy(
            onUpdate = viewModel::update,
            onExport = { exportLauncher.launch(fileName) },
            onDismissExport = viewModel::dismissExportMessage,
        ),
    )
}

@Composable
fun SettingsContent(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    val s = state.settings
    var exportWarning by rememberSaveable { mutableStateOf(false) }
    TabScreen(title = stringResource(R.string.nav_settings), modifier = modifier) {
        SectionHeader(stringResource(R.string.settings_cash))
        NavRow(Icons.Rounded.AccountBalanceWallet, stringResource(R.string.opening_title), stringResource(R.string.opening_hint), actions.onOpenOpening)
        NavRow(Icons.Rounded.Tune, stringResource(R.string.adjust_title), stringResource(R.string.adjust_hint), actions.onOpenAdjust)
        NavRow(Icons.Rounded.Repeat, stringResource(R.string.recurring_title), stringResource(R.string.recurring_hint), actions.onOpenRecurring)
        NavRow(Icons.Rounded.Category, stringResource(R.string.categories_title), stringResource(R.string.categories_hint), actions.onOpenCategories)

        SectionHeader(stringResource(R.string.settings_display), Modifier.padding(top = Spacing.s))
        NavRow(Icons.Rounded.CurrencyExchange, stringResource(R.string.currency_title), stringResource(R.string.currency_hint), actions.onOpenCurrency)
        FinanceCard(Modifier.fillMaxWidth()) {
            ChoiceRow(
                stringResource(R.string.settings_theme),
                listOf(
                    ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                    ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                    ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                ),
                selected = s.themeMode,
                onSelect = { mode -> actions.onUpdate { it.copy(themeMode = mode) } },
            )
            Spacer(Modifier.height(Spacing.l))
            ChoiceRow(
                stringResource(R.string.settings_number_format),
                listOf(NumberFormatStyle.COMMA_DECIMAL to "1.234,56", NumberFormatStyle.DOT_DECIMAL to "1,234.56"),
                selected = s.numberFormat,
                onSelect = { style -> actions.onUpdate { it.copy(numberFormat = style) } },
            )
            Spacer(Modifier.height(Spacing.l))
            ChoiceRow(
                stringResource(R.string.settings_date_format),
                listOf(DateFormatStyle.DMY to stringResource(R.string.settings_date_dmy), DateFormatStyle.MDY to stringResource(R.string.settings_date_mdy)),
                selected = s.dateFormat,
                onSelect = { style -> actions.onUpdate { it.copy(dateFormat = style) } },
            )
        }

        SectionHeader(stringResource(R.string.settings_data), Modifier.padding(top = Spacing.s))
        NavRow(Icons.Rounded.CloudUpload, stringResource(R.string.backup_title), stringResource(R.string.backup_settings_hint), actions.onOpenBackup)
        NavRow(Icons.Rounded.TableChart, stringResource(R.string.export_title), stringResource(R.string.export_hint), { exportWarning = true })
        state.exportMessage?.let { message ->
            Text(
                stringResource(if (message == ExportMessage.DONE) R.string.export_done else R.string.export_error),
                style = FinanceTheme.type.caption,
                color = if (message == ExportMessage.DONE) FinanceTheme.colors.positive else FinanceTheme.colors.danger,
            )
        }

        SectionHeader(stringResource(R.string.settings_about_section), Modifier.padding(top = Spacing.s))
        NavRow(Icons.Rounded.Shield, stringResource(R.string.privacy_title), stringResource(R.string.privacy_hint), actions.onOpenPrivacy)
        NavRow(Icons.Rounded.Info, stringResource(R.string.about_title), stringResource(R.string.about_hint), actions.onOpenAbout)
    }
    if (exportWarning) {
        ConfirmDialog(
            title = stringResource(R.string.export_warning_title),
            message = stringResource(R.string.export_warning_message),
            confirmText = stringResource(R.string.export_confirm),
            onConfirm = { exportWarning = false; actions.onExport() },
            onDismiss = { exportWarning = false },
        )
    }
}
