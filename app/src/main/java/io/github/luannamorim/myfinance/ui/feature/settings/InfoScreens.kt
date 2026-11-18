package io.github.luannamorim.myfinance.ui.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.ui.components.DetailScaffold
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing

/** What is stored, what is sent and what is encrypted (mirrors docs/SECURITY.md). */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    DetailScaffold(title = stringResource(R.string.privacy_title), onBack = onBack) {
        listOf(
            R.string.privacy_storage_title to R.string.privacy_storage,
            R.string.privacy_network_title to R.string.privacy_network,
            R.string.privacy_backup_title to R.string.privacy_backup,
            R.string.privacy_tracking_title to R.string.privacy_tracking,
        ).forEach { (title, body) -> InfoCard(stringResource(title), stringResource(body)) }
    }
}

@Composable
private fun InfoCard(title: String?, body: String) {
    val c = FinanceTheme.colors
    FinanceCard(Modifier.fillMaxWidth()) {
        if (title != null) Text(title, style = FinanceTheme.type.title, color = c.textPrimary, modifier = Modifier.padding(bottom = Spacing.xs))
        Text(body, style = FinanceTheme.type.body, color = c.textSecondary)
    }
}
