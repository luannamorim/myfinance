package io.github.luannamorim.myfinance.ui.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Language
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.domain.model.AppSettings
import io.github.luannamorim.myfinance.domain.model.DateFormatStyle
import io.github.luannamorim.myfinance.domain.model.NumberFormatStyle
import io.github.luannamorim.myfinance.domain.model.ThemeMode
import io.github.luannamorim.myfinance.ui.components.ChoiceRow
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.NavRow
import io.github.luannamorim.myfinance.ui.components.SectionHeader
import io.github.luannamorim.myfinance.ui.components.TabScreen
import io.github.luannamorim.myfinance.ui.theme.Spacing

data class SettingsActions(
    val onUpdate: ((AppSettings) -> AppSettings) -> Unit = {},
    val onOpenCategories: () -> Unit = {},
    val onOpenLanguage: (() -> Unit)? = null,
)

@Composable
fun SettingsScreen(actions: SettingsActions, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openLanguage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        {
            context.startActivity(
                Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        }
    } else {
        null
    }
    SettingsContent(state, actions.copy(onUpdate = viewModel::update, onOpenLanguage = openLanguage))
}

@Composable
fun SettingsContent(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    val s = state.settings
    TabScreen(title = stringResource(R.string.nav_settings), modifier = modifier) {
        SectionHeader(stringResource(R.string.settings_cash))
        NavRow(Icons.Rounded.Category, stringResource(R.string.categories_title), stringResource(R.string.categories_hint), actions.onOpenCategories)

        SectionHeader(stringResource(R.string.settings_display), Modifier.padding(top = Spacing.s))
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
        actions.onOpenLanguage?.let {
            NavRow(Icons.Rounded.Language, stringResource(R.string.settings_language), stringResource(R.string.settings_language_hint), it)
        }
    }
}
