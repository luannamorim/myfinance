package io.github.luannamorim.myfinance.ui.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.ui.components.AmountDisplay
import io.github.luannamorim.myfinance.ui.components.AmountTone
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.TilePair
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing

/** A balance tile: caption and a converted total (red when negative, "≈" when a rate is missing). */
@Composable
fun TotalTile(label: String, total: Total?, modifier: Modifier = Modifier) {
    FinanceCard(modifier) {
        Text(label, style = FinanceTheme.type.caption, color = FinanceTheme.colors.textSecondary)
        Spacer(Modifier.height(Spacing.s))
        total?.let {
            AmountDisplay(
                it.money,
                style = FinanceTheme.type.title.copy(fontFeatureSettings = "tnum"),
                tone = if (it.money.isNegative) AmountTone.NEGATIVE else AmountTone.NEUTRAL,
                approximate = it.incomplete,
            )
        }
    }
}

/** Two [TotalTile]s side by side, stacked when they don't fit. */
@Composable
fun TotalTiles(firstLabel: String, first: Total?, secondLabel: String, second: Total?, modifier: Modifier = Modifier) {
    TilePair(
        first = { TotalTile(firstLabel, first) },
        second = { TotalTile(secondLabel, second) },
        modifier = modifier,
    )
}
