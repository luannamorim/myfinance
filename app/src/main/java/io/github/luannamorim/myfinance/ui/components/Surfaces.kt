package io.github.luannamorim.myfinance.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.luannamorim.myfinance.ui.theme.FinanceShapes
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing

/** Flat card: hairline border, no shadow. */
@Composable
fun FinanceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Spacing.l,
    color: Color = FinanceTheme.colors.surface,
    borderColor: Color = FinanceTheme.colors.outline,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = BorderStroke(1.dp, borderColor)
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(contentPadding), content = content)
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = FinanceShapes.medium, color = color, border = border, content = inner)
    } else {
        Surface(modifier = modifier, shape = FinanceShapes.medium, color = color, border = border, content = inner)
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(top = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title.uppercase(),
            style = FinanceTheme.type.overline,
            color = FinanceTheme.colors.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
        action?.invoke()
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    delta: String? = null,
    deltaPositive: Boolean? = null,
    valueStyle: TextStyle = FinanceTheme.type.numericLarge,
) {
    val c = FinanceTheme.colors
    FinanceCard(modifier) {
        Text(label, style = FinanceTheme.type.caption, color = c.textSecondary)
        Spacer(Modifier.height(Spacing.s))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = valueStyle, color = c.textPrimary)
            if (unit != null) {
                Text(
                    " $unit",
                    style = FinanceTheme.type.label,
                    color = c.textSecondary,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
        if (delta != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                delta,
                style = FinanceTheme.type.numeric,
                color = when (deltaPositive) {
                    true -> c.positive
                    false -> c.negative
                    null -> c.textSecondary
                },
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FinanceMark(size = 40.dp, lineColor = FinanceTheme.colors.textTertiary)
        Spacer(Modifier.height(Spacing.l))
        Text(title, style = FinanceTheme.type.title, color = FinanceTheme.colors.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.xs))
        Text(message, style = FinanceTheme.type.body, color = FinanceTheme.colors.textSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(Spacing.l))
            action()
        }
    }
}
