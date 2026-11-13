package io.github.luannamorim.myfinance.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.luannamorim.myfinance.domain.cash.DisplayStatus
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.FinanceShapes

/** The status colour: the chip carries the colour so amounts can stay neutral (docs/BRAND.md). */
@Composable
fun DisplayStatus.color(): Color {
    val c = FinanceTheme.colors
    return when (this) {
        DisplayStatus.OVERDUE -> c.danger
        DisplayStatus.PENDING -> c.warning
        DisplayStatus.UPCOMING -> c.textSecondary
        DisplayStatus.PAID, DisplayStatus.RECEIVED -> c.positive
        DisplayStatus.CANCELLED -> c.textTertiary
    }
}

@Composable
fun StatusChip(status: DisplayStatus, modifier: Modifier = Modifier) {
    val c = FinanceTheme.colors
    Row(
        modifier.background(c.surfaceRaised, FinanceShapes.pill).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(status.color(), CircleShape))
        Text(status.label(), style = FinanceTheme.type.caption, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
    }
}
