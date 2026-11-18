package io.github.luannamorim.myfinance.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme

/** Shown under a total that couldn't convert every amount. */
@Composable
fun IncompleteNote(modifier: Modifier = Modifier) {
    Text(stringResource(R.string.rate_incomplete), style = FinanceTheme.type.caption, color = FinanceTheme.colors.warning, modifier = modifier)
}
