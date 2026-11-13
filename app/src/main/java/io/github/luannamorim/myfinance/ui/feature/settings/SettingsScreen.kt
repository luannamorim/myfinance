package io.github.luannamorim.myfinance.ui.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.ui.components.EmptyState
import io.github.luannamorim.myfinance.ui.components.TabScreen

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    TabScreen(title = stringResource(R.string.nav_settings), modifier = modifier) {
        EmptyState(stringResource(R.string.settings_empty_title), stringResource(R.string.settings_empty_message))
    }
}
