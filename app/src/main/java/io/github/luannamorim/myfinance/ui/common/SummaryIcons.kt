package io.github.luannamorim.myfinance.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing

/** Icons of the summary numbers on Overview and Cash Flow. Neutral by design (docs/BRAND.md: no coloured icons). */
object SummaryIcons {
    val Income: ImageVector get() = Icons.Rounded.ArrowUpward
    val Expenses: ImageVector get() = Icons.Rounded.ArrowDownward
    val Cash: ImageVector get() = Icons.Rounded.AccountBalanceWallet
    val Projected: ImageVector get() = Icons.Rounded.Update
    val Transfers: ImageVector get() = TransferIcon

    /** "=": income − expenses = result. Material has no equals sign; these are two of Icons.Rounded.Remove's bars. */
    val Result: ImageVector by lazy {
        materialIcon(name = "Rounded.Equal") {
            materialPath {
                bar(top = 8f)
                bar(top = 14f)
            }
        }
    }

    /** A 14 × 2 bar with round ends from x = 5 to 19, like Icons.Rounded.Remove's. */
    private fun androidx.compose.ui.graphics.vector.PathBuilder.bar(top: Float) {
        moveTo(6f, top)
        horizontalLineToRelative(12f)
        curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
        reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
        horizontalLineTo(6f)
        curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
        reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
        close()
    }
}

/** [label] with a small neutral [icon] on its left; a long label wraps instead of pushing the icon out. */
@Composable
fun IconLabel(icon: ImageVector, iconSize: Dp, modifier: Modifier = Modifier, label: @Composable () -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = FinanceTheme.colors.textSecondary, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(Spacing.s))
        Box(Modifier.weight(1f, fill = false)) { label() }
    }
}
