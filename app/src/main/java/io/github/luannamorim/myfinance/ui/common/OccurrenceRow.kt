package io.github.luannamorim.myfinance.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.github.luannamorim.myfinance.domain.cash.DisplayStatus
import io.github.luannamorim.myfinance.domain.cash.OccurrenceStatus
import io.github.luannamorim.myfinance.domain.model.Category
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.Occurrence
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.ui.components.AmountDisplay
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.feature.settings.CategoryBadge
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing
import java.time.LocalDate

/** A transaction line for lists: actual values once confirmed, expected values before. */
data class OccurrenceRow(
    val id: Long,
    val name: String,
    val category: Category?,
    val kind: EntryKind,
    val amount: Money,
    val date: LocalDate,
    val status: DisplayStatus,
    val occurrence: Occurrence,
) {
    companion object {
        fun of(o: Occurrence, categories: Map<Long, Category>, today: LocalDate): OccurrenceRow {
            val confirmed = o.state == OccurrenceState.CONFIRMED
            return OccurrenceRow(
                id = o.id,
                name = o.name,
                category = categories[o.categoryId],
                kind = o.kind,
                amount = if (confirmed) o.actual ?: o.expected else o.expected,
                date = if (confirmed) o.actualDate ?: o.dueDate else o.dueDate,
                status = OccurrenceStatus.of(o, today),
                occurrence = o,
            )
        }
    }
}

/** Neutral amount with a sign; the status chip carries the colour (docs/BRAND.md, money colour rule). */
@Composable
fun OccurrenceRowItem(row: OccurrenceRow, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    FinanceCard(onClick = onClick, modifier = modifier.fillMaxWidth(), contentPadding = Spacing.m) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(CategoryIcons.of(row.category?.icon ?: "category"), dimmed = row.status == DisplayStatus.CANCELLED)
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(row.name, style = FinanceTheme.type.bodyStrong, color = if (row.status == DisplayStatus.CANCELLED) c.textTertiary else c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formats.dates.dayMonth(row.date) + " · ", style = FinanceTheme.type.caption, color = c.textSecondary)
                    Text(row.status.label(), style = FinanceTheme.type.caption, color = row.status.color())
                }
            }
            Spacer(Modifier.width(Spacing.s))
            Column(horizontalAlignment = Alignment.End) {
                AmountDisplay(if (row.kind == EntryKind.EXPENSE) -row.amount else row.amount, signed = row.kind == EntryKind.INCOME)
                trailing?.invoke()
            }
        }
    }
}
