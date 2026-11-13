package io.github.luannamorim.myfinance.ui.feature.portfolio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.ui.common.CategoryIcons
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.common.displayName
import io.github.luannamorim.myfinance.ui.components.AdaptiveRow
import io.github.luannamorim.myfinance.ui.components.AmountDisplay
import io.github.luannamorim.myfinance.ui.components.AmountTone
import io.github.luannamorim.myfinance.ui.components.DonutChart
import io.github.luannamorim.myfinance.ui.components.DonutSlice
import io.github.luannamorim.myfinance.ui.components.EmptyState
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.FinanceChip
import io.github.luannamorim.myfinance.ui.components.IncompleteNote
import io.github.luannamorim.myfinance.ui.components.LegendDot
import io.github.luannamorim.myfinance.ui.components.NavRow
import io.github.luannamorim.myfinance.ui.components.PrimaryButton
import io.github.luannamorim.myfinance.ui.components.RateFooter
import io.github.luannamorim.myfinance.ui.components.SectionHeader
import io.github.luannamorim.myfinance.ui.components.TabScreen
import io.github.luannamorim.myfinance.ui.components.TextAction
import io.github.luannamorim.myfinance.ui.feature.settings.CategoryBadge
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing

data class PortfolioActions(
    val onGroup: (AssetGroup?) -> Unit = {},
    val onSelect: (Long?) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onOpen: (Long) -> Unit = {},
    val onCharts: () -> Unit = {},
)

@Composable
fun PortfolioScreen(actions: PortfolioActions, viewModel: PortfolioViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PortfolioContent(state, actions.copy(onGroup = viewModel::setGroup, onSelect = viewModel::select))
}

@Composable
fun PortfolioContent(state: PortfolioUiState, actions: PortfolioActions, modifier: Modifier = Modifier) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    TabScreen(
        title = stringResource(R.string.nav_portfolio),
        modifier = modifier,
        actions = { IconButton(onClick = actions.onAdd) { Icon(Icons.Rounded.Add, stringResource(R.string.portfolio_add), tint = c.textPrimary) } },
    ) {
        if (state.loading) return@TabScreen
        if (state.empty) {
            EmptyState(
                stringResource(R.string.portfolio_empty_title),
                stringResource(R.string.portfolio_empty_message),
                action = { PrimaryButton(stringResource(R.string.portfolio_add), onClick = actions.onAdd) },
            )
            return@TabScreen
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            FinanceChip(stringResource(R.string.portfolio_all), selected = state.group == null, onClick = { actions.onGroup(null) })
            FinanceChip(stringResource(R.string.group_investment), selected = state.group == AssetGroup.INVESTMENT, onClick = { actions.onGroup(AssetGroup.INVESTMENT) })
            FinanceChip(stringResource(R.string.group_personal), selected = state.group == AssetGroup.PERSONAL, onClick = { actions.onGroup(AssetGroup.PERSONAL) })
        }
        val totals = state.totals
        val donut = state.slices.mapIndexed { i, (slice, category) ->
            DonutSlice(slice.categoryId, slice.share.toFloat(), c.chart[i % c.chart.size], category?.displayName().orEmpty())
        }
        FinanceCard(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DonutChart(
                    slices = donut,
                    selected = state.selectedCategory,
                    onSelect = actions.onSelect,
                    centerTitle = stringResource(R.string.portfolio_total),
                    centerValue = totals?.let { (if (it.value.incomplete) "≈ " else "") + formats.money.format(it.value.money) }.orEmpty(),
                )
            }
            Spacer(Modifier.height(Spacing.l))
            state.slices.forEachIndexed { i, (slice, category) ->
                val selected = state.selectedCategory == slice.categoryId
                Row(
                    Modifier.fillMaxWidth().clickable { actions.onSelect(if (selected) null else slice.categoryId) }.padding(vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AdaptiveRow(
                        main = { LegendDot(if (selected) c.accent else c.chart[i % c.chart.size], category?.displayName().orEmpty()) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(formats.money.formatPercent(slice.share), style = FinanceTheme.type.numeric, color = c.textSecondary, maxLines = 1)
                                Spacer(Modifier.width(Spacing.m))
                                AmountDisplay(slice.value, approximate = slice.incomplete)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (state.selectedCategory != null) TextAction(stringResource(R.string.portfolio_show_all), onClick = { actions.onSelect(null) })
        }
        totals?.let { t ->
            FinanceCard(Modifier.fillMaxWidth()) {
                TotalRow(stringResource(R.string.portfolio_value), t.value)
                TotalRow(stringResource(R.string.portfolio_invested), t.invested)
                TotalRow(stringResource(R.string.portfolio_gain), t.gain, AmountTone.AUTO)
                t.simpleReturn?.let { r ->
                    AdaptiveRow(
                        main = { Text(stringResource(R.string.portfolio_return), style = FinanceTheme.type.body, color = c.textSecondary) },
                        trailing = { Text(formats.money.formatPercent(r, signed = true), style = FinanceTheme.type.numeric, color = if (r.signum() < 0) c.negative else c.positive) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!t.realizedClosed.money.isZero) TotalRow(stringResource(R.string.portfolio_realized), t.realizedClosed, AmountTone.AUTO)
                t.lastValuationDate?.let {
                    Text(stringResource(R.string.portfolio_last_valuation, formats.dates.date(it)), style = FinanceTheme.type.caption, color = c.textTertiary, modifier = Modifier.padding(top = Spacing.s))
                }
                if (t.value.incomplete || t.gain.incomplete) IncompleteNote(Modifier.padding(top = Spacing.s))
            }
        }
        state.assets.groupBy { it.category }.forEach { (category, rows) ->
            SectionHeader(category?.displayName().orEmpty(), Modifier.padding(top = Spacing.s))
            rows.forEach { AssetCard(it, actions.onOpen) }
        }
        if (state.closed.isNotEmpty() && state.selectedCategory == null) {
            SectionHeader(stringResource(R.string.portfolio_closed), Modifier.padding(top = Spacing.s))
            state.closed.forEach { AssetCard(it, actions.onOpen) }
        }
        NavRow(Icons.AutoMirrored.Rounded.ShowChart, stringResource(R.string.portfolio_charts), stringResource(R.string.portfolio_charts_hint), actions.onCharts, Modifier.padding(top = Spacing.s))
        if (state.usesConversion) RateFooter(state.rate)
    }
}

@Composable
private fun TotalRow(label: String, total: Total, tone: AmountTone = AmountTone.NEUTRAL) {
    AdaptiveRow(
        main = { Text(label, style = FinanceTheme.type.body, color = FinanceTheme.colors.textSecondary) },
        trailing = { AmountDisplay(total.money, tone = tone, signed = tone == AmountTone.AUTO, approximate = total.incomplete) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AssetCard(row: AssetRow, onOpen: (Long) -> Unit) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    val s = row.snapshot
    FinanceCard(onClick = { onOpen(s.asset.id) }, modifier = Modifier.fillMaxWidth(), contentPadding = Spacing.m) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(CategoryIcons.of(row.category?.icon ?: "category"), dimmed = s.closed)
            Spacer(Modifier.width(Spacing.m))
            AdaptiveRow(
                main = {
                    Column {
                        Text(s.asset.name, style = FinanceTheme.type.bodyStrong, color = if (s.closed) c.textTertiary else c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
                            AmountDisplay(s.gain, style = FinanceTheme.type.caption.copy(fontFeatureSettings = "tnum"), signed = true, tone = AmountTone.AUTO)
                            s.simpleReturn?.let { Text(" · " + formats.money.formatPercent(it, signed = true), style = FinanceTheme.type.caption, color = c.textSecondary, maxLines = 1) }
                        }
                    }
                },
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        AmountDisplay(s.value)
                        row.displayValue?.let { AmountDisplay(it.money, style = FinanceTheme.type.caption.copy(fontFeatureSettings = "tnum"), approximate = true) }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
