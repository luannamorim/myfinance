package io.github.luannamorim.myfinance.ui.feature.cashflow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.repository.CashRepository
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.domain.cash.RecurrenceRule
import io.github.luannamorim.myfinance.domain.model.Category
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.ui.common.CategoryIcons
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.common.TransferIcon
import io.github.luannamorim.myfinance.ui.common.directionLabel
import io.github.luannamorim.myfinance.ui.common.displayName
import io.github.luannamorim.myfinance.ui.common.label
import io.github.luannamorim.myfinance.ui.components.AmountDisplay
import io.github.luannamorim.myfinance.ui.components.DetailScaffold
import io.github.luannamorim.myfinance.ui.components.EmptyState
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.PrimaryButton
import io.github.luannamorim.myfinance.ui.components.SectionHeader
import io.github.luannamorim.myfinance.ui.feature.settings.CategoryBadge
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class TemplateRow(
    val id: Long,
    val name: String,
    val kind: EntryKind,
    val amount: Money,
    val rule: RecurrenceRule,
    val category: Category?,
    val nextDue: LocalDate?,
    val active: Boolean,
)

data class RecurringUiState(val loading: Boolean = true, val active: List<TemplateRow> = emptyList(), val inactive: List<TemplateRow> = emptyList())

@HiltViewModel
class RecurringListViewModel @Inject constructor(cash: CashRepository, categories: CategoryRepository, time: TimeSource) : ViewModel() {
    val state: StateFlow<RecurringUiState> = combine(cash.ledger, categories.observeAll()) { ledger, cats ->
        val today = time.today()
        val byId = cats.associateBy { it.id }
        val rows = ledger.templates.map { t ->
            val next = ledger.occurrences
                .filter { it.templateId == t.id && it.state == OccurrenceState.PENDING && !it.dueDate.isBefore(today) }
                .minOfOrNull { it.dueDate }
            TemplateRow(t.id, t.name, t.kind, t.expected, t.rule, byId[t.categoryId], next, t.active)
        }.sortedWith(compareBy<TemplateRow, LocalDate?>(nullsLast()) { it.nextDue }.thenBy { it.name.lowercase() })
        RecurringUiState(loading = false, active = rows.filter { it.active }, inactive = rows.filterNot { it.active })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringUiState())
}

@Composable
fun RecurringListScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, onAdd: () -> Unit, viewModel: RecurringListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RecurringListContent(state, onBack, onOpen, onAdd)
}

@Composable
fun RecurringListContent(state: RecurringUiState, onBack: () -> Unit, onOpen: (Long) -> Unit, onAdd: () -> Unit) {
    DetailScaffold(
        title = stringResource(R.string.recurring_title),
        onBack = onBack,
        actions = { IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, stringResource(R.string.editor_new_title), tint = FinanceTheme.colors.textPrimary) } },
    ) {
        if (state.loading) return@DetailScaffold
        if (state.active.isEmpty() && state.inactive.isEmpty()) {
            EmptyState(
                stringResource(R.string.recurring_empty_title),
                stringResource(R.string.recurring_empty_message),
                action = { PrimaryButton(stringResource(R.string.editor_new_title), onClick = onAdd) },
            )
            return@DetailScaffold
        }
        if (state.active.isNotEmpty()) {
            SectionHeader(stringResource(R.string.recurring_active))
            state.active.forEach { TemplateCard(it, onOpen) }
        }
        if (state.inactive.isNotEmpty()) {
            SectionHeader(stringResource(R.string.recurring_inactive), Modifier.padding(top = Spacing.s))
            state.inactive.forEach { TemplateCard(it, onOpen) }
        }
    }
}

@Composable
private fun TemplateCard(row: TemplateRow, onOpen: (Long) -> Unit) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    FinanceCard(onClick = { onOpen(row.id) }, modifier = Modifier.fillMaxWidth(), contentPadding = Spacing.m) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(if (row.kind.isTransfer) TransferIcon else CategoryIcons.of(row.category?.icon ?: "category"), dimmed = !row.active)
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(row.name, style = FinanceTheme.type.bodyStrong, color = if (row.active) c.textPrimary else c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val next = row.nextDue?.let { stringResource(R.string.recurring_next, formats.dates.dayMonth(it)) }
                Text(
                    listOfNotNull(row.rule.label(), if (row.kind.isTransfer) row.kind.directionLabel() else row.category?.displayName(), next).joinToString(" · "),
                    style = FinanceTheme.type.caption,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.s))
            AmountDisplay(if (row.kind.cashInflow) row.amount else -row.amount, signed = row.kind.cashInflow)
        }
    }
}
