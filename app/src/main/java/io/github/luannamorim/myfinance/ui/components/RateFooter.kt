package io.github.luannamorim.myfinance.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.domain.model.ExchangeRate
import io.github.luannamorim.myfinance.domain.model.RateSource
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme

/** "US$ 1 = R$ 5,6143 · PTAX 12/11 · Update": the rate behind every converted total, always with its date. */
@Composable
fun RateFooter(rate: ExchangeRate?, modifier: Modifier = Modifier, onUpdate: (() -> Unit)? = null, updating: Boolean = false) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (rate == null) {
                stringResource(R.string.rate_missing)
            } else {
                stringResource(
                    if (rate.source == RateSource.MANUAL) R.string.rate_footer_manual else R.string.rate_footer,
                    formats.money.formatRate(rate.rate),
                    formats.dates.dayMonth(rate.date),
                )
            },
            style = FinanceTheme.type.caption,
            color = if (rate == null) c.warning else c.textTertiary,
            modifier = Modifier.weight(1f),
        )
        if (onUpdate != null) TextAction(stringResource(R.string.rate_update), onClick = onUpdate, enabled = !updating)
    }
}

/** Shown under a total that couldn't convert every amount. */
@Composable
fun IncompleteNote(modifier: Modifier = Modifier) {
    Text(stringResource(R.string.rate_incomplete), style = FinanceTheme.type.caption, color = FinanceTheme.colors.warning, modifier = modifier)
}
