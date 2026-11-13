package io.github.luannamorim.myfinance.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing

/** A card that opens another screen: icon, title, hint and a chevron. */
@Composable
fun NavRow(icon: ImageVector, title: String, hint: String?, onClick: () -> Unit, modifier: Modifier = Modifier, error: Boolean = false) {
    val c = FinanceTheme.colors
    FinanceCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (error) c.danger else c.textSecondary)
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(title, style = FinanceTheme.type.bodyStrong, color = c.textPrimary)
                if (hint != null) Text(hint, style = FinanceTheme.type.caption, color = if (error) c.danger else c.textSecondary)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textTertiary)
        }
    }
}

/** A full-width switch with a title and an optional hint; the whole row toggles. */
@Composable
fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = FinanceTheme.colors
    Row(
        modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = FinanceTheme.type.bodyStrong, color = c.textPrimary)
            if (hint != null) Text(hint, style = FinanceTheme.type.caption, color = c.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent))
    }
}

/** A caption followed by single-choice chips. */
@Composable
fun <T> ChoiceRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = FinanceTheme.type.caption, color = FinanceTheme.colors.textSecondary)
        Spacer(Modifier.height(Spacing.s))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            options.forEach { (value, text) -> FinanceChip(text, selected = value == selected, onClick = { onSelect(value) }) }
        }
    }
}
