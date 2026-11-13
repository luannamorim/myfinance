package io.github.luannamorim.myfinance.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.luannamorim.myfinance.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.luannamorim.myfinance.ui.theme.FinanceShapes
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing

/**
 * Large +/- control for gym use (sweaty hands): 56dp targets, value tappable for direct entry.
 */
@Composable
fun NumberStepper(
    value: String,
    label: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    onValueClick: (() -> Unit)? = null,
    unit: String? = null,
    valueStyle: TextStyle = FinanceTheme.type.numericLarge,
    decrementDescription: String = stringResource(R.string.a11y_decrease, label),
    incrementDescription: String = stringResource(R.string.a11y_increase, label),
) {
    val c = FinanceTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = FinanceTheme.type.caption, color = c.textSecondary)
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(onClick = onDecrement, description = decrementDescription) {
                Icon(Icons.Rounded.Remove, contentDescription = null)
            }
            Box(
                Modifier
                    .weight(1f)
                    .then(if (onValueClick != null) Modifier.clickable(role = Role.Button, onClick = onValueClick) else Modifier)
                    .padding(horizontal = Spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                val text = buildAnnotatedString {
                    append(value)
                    if (unit != null) {
                        withStyle(SpanStyle(fontSize = 0.45.em, color = c.textSecondary, fontWeight = FontWeight.Medium)) {
                            append(" $unit")
                        }
                    }
                }
                // Shrinks instead of wrapping when two steppers share a narrow row.
                BasicText(
                    text = text,
                    style = valueStyle.copy(color = c.textPrimary, textAlign = TextAlign.Center),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = valueStyle.fontSize, stepSize = 1.sp),
                )
            }
            StepButton(onClick = onIncrement, description = incrementDescription) {
                Icon(Icons.Rounded.Add, contentDescription = null)
            }
        }
    }
}

@Composable
private fun StepButton(onClick: () -> Unit, description: String, content: @Composable () -> Unit) {
    val c = FinanceTheme.colors
    Surface(
        onClick = onClick,
        shape = FinanceShapes.pill,
        color = c.surfaceRaised,
        contentColor = c.textPrimary,
        border = BorderStroke(1.dp, c.outline),
        modifier = Modifier.size(56.dp).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** Multi-select chips in a scrolling row. With [allLabel], a leading chip selects or clears every option. */
@Composable
fun <T> FilterChipRow(
    options: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String? = null,
    onToggleAll: () -> Unit = {},
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (allLabel != null) {
            FinanceChip(text = allLabel, selected = selected.containsAll(options), onClick = onToggleAll)
        }
        options.forEach { option ->
            FinanceChip(text = label(option), selected = option in selected, onClick = { onToggle(option) })
        }
    }
}

@Composable
fun FinanceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = FinanceTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        shape = FinanceShapes.pill,
        color = if (selected) c.textPrimary else c.surface,
        contentColor = if (selected) c.background else c.textSecondary,
        border = BorderStroke(1.dp, if (selected) c.textPrimary else c.outline),
    ) {
        Text(text, style = FinanceTheme.type.label, maxLines = 1, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
    }
}
