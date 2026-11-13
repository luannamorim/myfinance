package io.github.luannamorim.myfinance.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import java.time.YearMonth

/** ‹ November 2026 › with 48 dp arrows. */
@Composable
fun MonthSwitcher(month: YearMonth, onMonth: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    val c = FinanceTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.a11y_previous_month), tint = c.textSecondary)
        }
        Text(
            LocalFormats.current.dates.monthYear(month),
            style = FinanceTheme.type.title,
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        IconButton(onClick = { onMonth(month.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.a11y_next_month), tint = c.textSecondary)
        }
    }
}
