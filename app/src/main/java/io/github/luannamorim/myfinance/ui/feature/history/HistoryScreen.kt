package io.github.luannamorim.myfinance.ui.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.domain.cash.HistoryFilter
import io.github.luannamorim.myfinance.domain.cash.HistoryProgress
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.common.OccurrenceRowItem
import io.github.luannamorim.myfinance.ui.common.seedNameRes
import io.github.luannamorim.myfinance.ui.components.AmountDisplay
import io.github.luannamorim.myfinance.ui.components.ContentMaxWidth
import io.github.luannamorim.myfinance.ui.components.EmptyState
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.FinanceChip
import io.github.luannamorim.myfinance.ui.components.FinanceTextField
import io.github.luannamorim.myfinance.ui.components.MonthSwitcher
import io.github.luannamorim.myfinance.ui.components.TabHeader
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing

@Composable
fun HistoryScreen(onOpen: (Long) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.categories) {
        viewModel.setCategoryNames(state.categories.associate { c -> c.id to (c.name ?: seedNameRes(c.seedKey)?.let(context::getString).orEmpty()) })
    }
    HistoryContent(state, onOpen, viewModel::update)
}

/** One month at a time (the switcher moves it), searchable, narrowed to what is open or done by two chips. */
@Composable
fun HistoryContent(
    state: HistoryUiState,
    onOpen: (Long) -> Unit,
    onFilter: ((HistoryFilter) -> HistoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FinanceTheme.colors
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
            item(key = "month") { MonthSwitcher(f.month, { m -> onFilter { it.copy(month = m) } }) }
            item(key = "progress") {
                // One at a time; tapping the selected chip again shows the whole month.
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    ProgressChip(stringResource(R.string.history_open), HistoryProgress.OPEN, f.progress, onFilter)
                    ProgressChip(stringResource(R.string.history_done), HistoryProgress.DONE, f.progress, onFilter)
                }
            }
            if (state.items.isEmpty()) {
                item {
                    val narrowed = f.query.isNotBlank() || f.progress != null
                    Text(
                        stringResource(if (narrowed) R.string.history_no_results else R.string.history_empty_month),
                        style = FinanceTheme.type.body,
                        color = c.textSecondary,
                        modifier = Modifier.padding(top = Spacing.l),
                    )
                }
            }
            items(state.items, key = { item -> if (item is HistoryItem.Transaction) "t-${item.row.id}" else "a-${(item as HistoryItem.Adjustment).entry.adjustment.id}" }) { item ->
                when (item) {
                    is HistoryItem.Transaction -> OccurrenceRowItem(item.row, onClick = { onOpen(item.row.id) })
                    is HistoryItem.Adjustment -> AdjustmentRow(item)
                }
            }
        }
    }
}

@Composable
private fun ProgressChip(label: String, value: HistoryProgress, selected: HistoryProgress?, onFilter: ((HistoryFilter) -> HistoryFilter) -> Unit) {
    FinanceChip(label, selected = selected == value, onClick = { onFilter { it.copy(progress = if (it.progress == value) null else value) } })
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
