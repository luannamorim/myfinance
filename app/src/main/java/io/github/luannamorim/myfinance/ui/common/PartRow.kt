package io.github.luannamorim.myfinance.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.ui.components.AdaptiveRow
import io.github.luannamorim.myfinance.ui.components.AmountDisplay
import io.github.luannamorim.myfinance.ui.components.AmountTone
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing

/** A part of the amount on the line above (e.g. what the exchange rate did to a gain): indented, smaller, signed. */
@Composable
fun PartRow(label: String, total: Total, modifier: Modifier = Modifier) {
    val type = FinanceTheme.type
    AdaptiveRow(
        main = { Text(label, style = type.label, color = FinanceTheme.colors.textSecondary) },
        trailing = {
            AmountDisplay(total.money, style = type.label.copy(fontFeatureSettings = "tnum"), signed = true, tone = AmountTone.AUTO, approximate = total.incomplete)
        },
        modifier = modifier.fillMaxWidth().padding(start = Spacing.m),
    )
}
