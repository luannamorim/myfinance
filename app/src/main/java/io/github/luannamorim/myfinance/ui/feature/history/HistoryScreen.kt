package io.github.luannamorim.myfinance.ui.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.domain.cash.DisplayStatus
import io.github.luannamorim.myfinance.domain.cash.HistoryFilter
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.ui.common.CategoryPicker
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.common.OccurrenceRowItem
import io.github.luannamorim.myfinance.ui.common.label
import io.github.luannamorim.myfinance.ui.common.seedNameRes
import io.github.luannamorim.myfinance.ui.components.AmountDisplay
import io.github.luannamorim.myfinance.ui.components.ContentMaxWidth
import io.github.luannamorim.myfinance.ui.components.DateSelector
import io.github.luannamorim.myfinance.ui.components.EmptyState
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.FinanceChip
import io.github.luannamorim.myfinance.ui.components.FinanceTextField
import io.github.luannamorim.myfinance.ui.components.PrimaryButton
import io.github.luannamorim.myfinance.ui.components.SecondaryButton
import io.github.luannamorim.myfinance.ui.components.SectionHeader
import io.github.luannamorim.myfinance.ui.components.TabHeader
import io.github.luannamorim.myfinance.ui.components.TextAction
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing

@Composable
fun HistoryScreen(onOpen: (Long) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.categories) {
        viewModel.setCategoryNames(state.categories.associate { c -> c.id to (c.name ?: seedNameRes(c.seedKey)?.let(context::getString).orEmpty()) })
    }
    HistoryContent(state, onOpen, viewModel::update, viewModel::clear)
}

private val statusOptions = listOf(DisplayStatus.OVERDUE, DisplayStatus.PENDING, DisplayStatus.UPCOMING, DisplayStatus.PAID, DisplayStatus.RECEIVED, DisplayStatus.CANCELLED)

@Composable
fun HistoryContent(
    state: HistoryUiState,
    onOpen: (Long) -> Unit,
    onFilter: ((HistoryFilter) -> HistoryFilter) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    var sheet by rememberSaveable { mutableStateOf(false) }
    val f = state.filter
    Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item { TabHeader(stringResource(R.string.nav_history)) }
            if (state.loading) return@LazyColumn
            if (!state.hasAnyData) {
                item { EmptyState(stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_message)) }
                return@LazyColumn
            }
            item {
                FinanceTextField(value = f.query, onValueChange = { q -> onFilter { it.copy(query = q) } }, label = stringResource(R.string.history_search))
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FinanceChip(stringResource(R.string.kind_income), selected = EntryKind.INCOME in f.kinds, onClick = { onFilter { it.copy(kinds = it.kinds.toggle(EntryKind.INCOME)) } })
                    FinanceChip(stringResource(R.string.kind_expense), selected = EntryKind.EXPENSE in f.kinds, onClick = { onFilter { it.copy(kinds = it.kinds.toggle(EntryKind.EXPENSE)) } })
                    Currency.entries.forEach { cur ->
                        FinanceChip(cur.symbol, selected = cur in f.currencies, onClick = { onFilter { it.copy(currencies = it.currencies.toggle(cur)) } })
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    statusOptions.forEach { s -> FinanceChip(s.label(), selected = s in f.statuses, onClick = { onFilter { it.copy(statuses = it.statuses.toggle(s)) } }) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextAction(stringResource(R.string.history_more_filters), onClick = { sheet = true }, color = c.textPrimary)
                    val extra = f.categoryIds.size + listOfNotNull(f.from, f.to).size
                    if (extra > 0) Text("($extra)", style = FinanceTheme.type.caption, color = c.textSecondary)
                    Spacer(Modifier.weight(1f))
                    if (!f.isEmpty) TextAction(stringResource(R.string.history_clear), onClick = onClear, color = c.textSecondary)
                }
            }
            if (state.months.isEmpty()) {
                item { Text(stringResource(R.string.history_no_results), style = FinanceTheme.type.body, color = c.textSecondary, modifier = Modifier.padding(top = Spacing.l)) }
            }
            state.months.forEach { (month, items) ->
                item(key = "m-$month") { SectionHeader(formats.dates.monthYear(month), Modifier.padding(top = Spacing.m)) }
                items(items, key = { item -> if (item is HistoryItem.Transaction) "t-${item.row.id}" else "a-${(item as HistoryItem.Adjustment).entry.adjustment.id}" }) { item ->
                    when (item) {
                        is HistoryItem.Transaction -> OccurrenceRowItem(item.row, onClick = { onOpen(item.row.id) })
                        is HistoryItem.Adjustment -> AdjustmentRow(item)
                    }
                }
            }
        }
    }
    if (sheet) FilterSheet(state, onFilter, onDismiss = { sheet = false })
}

@Composable
private fun AdjustmentRow(item: HistoryItem.Adjustment) {
    val c = FinanceTheme.colors
    val a = item.entry.adjustment
    FinanceCard(Modifier.fillMaxWidth(), contentPadding = Spacing.m) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.balance_adjustment), style = FinanceTheme.type.bodyStrong, color = c.textPrimary)
                Text(listOfNotNull(LocalFormats.current.dates.dayMonth(a.date), a.note).joinToString(" · "), style = FinanceTheme.type.caption, color = c.textSecondary)
            }
            Spacer(Modifier.width(Spacing.s))
            AmountDisplay(a.amount, signed = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(state: HistoryUiState, onFilter: ((HistoryFilter) -> HistoryFilter) -> Unit, onDismiss: () -> Unit) {
    val c = FinanceTheme.colors
    val f = state.filter
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surfaceRaised) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(stringResource(R.string.history_filters), style = FinanceTheme.type.headline, color = c.textPrimary)
            Text(stringResource(R.string.editor_category), style = FinanceTheme.type.caption, color = c.textSecondary)
            // Multi-select: reuse the picker, toggling the tapped category.
            state.categories.filterNot { it.archived }.groupBy { it.kind }.forEach { (_, list) ->
                CategoryPickerMulti(list, f.categoryIds) { id -> onFilter { it.copy(categoryIds = it.categoryIds.toggle(id)) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                f.from?.let { DateSelector(stringResource(R.string.history_from), it, { d -> onFilter { x -> x.copy(from = d) } }) }
                    ?: SecondaryButton(stringResource(R.string.history_from), onClick = { onFilter { it.copy(from = state.today.withDayOfMonth(1)) } })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                f.to?.let { DateSelector(stringResource(R.string.history_to), it, { d -> onFilter { x -> x.copy(to = d) } }) }
                    ?: SecondaryButton(stringResource(R.string.history_to), onClick = { onFilter { it.copy(to = state.today) } })
            }
            if (f.from != null || f.to != null) {
                TextAction(stringResource(R.string.history_any_date), onClick = { onFilter { it.copy(from = null, to = null) } }, color = c.textSecondary)
            }
            PrimaryButton(stringResource(R.string.history_apply), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CategoryPickerMulti(categories: List<io.github.luannamorim.myfinance.domain.model.Category>, selected: Set<Long>, onToggle: (Long) -> Unit) {
    // CategoryPicker highlights one id; draw it per selection state by passing each selected id.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        categories.forEach { category ->
            CategoryPicker(listOf(category), if (category.id in selected) category.id else null, onToggle)
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
