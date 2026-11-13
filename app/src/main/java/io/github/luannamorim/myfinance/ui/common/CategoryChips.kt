package io.github.luannamorim.myfinance.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.luannamorim.myfinance.domain.model.Category
import io.github.luannamorim.myfinance.ui.theme.FinanceShapes
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing

/** Category picker: icon + name pills, the selected one inverted (like FinanceChip). */
@Composable
fun CategoryPicker(categories: List<Category>, selected: Long?, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        categories.forEach { category ->
            val isSelected = category.id == selected
            val c = FinanceTheme.colors
            Surface(
                onClick = { onSelect(category.id) },
                shape = FinanceShapes.pill,
                color = if (isSelected) c.textPrimary else c.surface,
                contentColor = if (isSelected) c.background else c.textSecondary,
                border = BorderStroke(1.dp, if (isSelected) c.textPrimary else c.outline),
                modifier = Modifier.semantics { this.selected = isSelected; role = Role.RadioButton },
            ) {
                Row(Modifier.padding(start = 12.dp, end = 14.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(CategoryIcons.of(category.icon), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(category.displayName(), style = FinanceTheme.type.label, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
