package io.github.luannamorim.myfinance.ui.feature.asset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.domain.model.AssetFlow
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.ui.common.ActionErrorDialog
import io.github.luannamorim.myfinance.ui.common.CategoryIcons
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.common.StoredImage
import io.github.luannamorim.myfinance.ui.common.displayName
import io.github.luannamorim.myfinance.ui.components.AdaptiveRow
import io.github.luannamorim.myfinance.ui.components.AmountDisplay
import io.github.luannamorim.myfinance.ui.components.AmountTone
import io.github.luannamorim.myfinance.ui.components.ConfirmDialog
import io.github.luannamorim.myfinance.ui.components.DetailScaffold
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.FinanceLineChart
import io.github.luannamorim.myfinance.ui.components.LegendDot
import io.github.luannamorim.myfinance.ui.components.LineSeries
import io.github.luannamorim.myfinance.ui.components.PrimaryButton
import io.github.luannamorim.myfinance.ui.components.RateFooter
import io.github.luannamorim.myfinance.ui.components.SecondaryButton
import io.github.luannamorim.myfinance.ui.components.SectionHeader
import io.github.luannamorim.myfinance.ui.feature.settings.CategoryBadge
import io.github.luannamorim.myfinance.ui.theme.FinanceShapes
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing
import java.math.BigDecimal
import java.time.LocalDate

data class AssetDetailActions(
    val onBack: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onOpenSheet: (AssetSheet?) -> Unit = {},
    val onAddValuation: (LocalDate, Money, String?) -> Unit = { _, _, _ -> },
    val onAddFlow: (FlowKind, LocalDate, Money, BigDecimal?, String?, Money?, Money?) -> Unit = { _, _, _, _, _, _, _ -> },
    val onDismissError: () -> Unit = {},
    val onDeleteFlow: (Long) -> Unit = {},
    val onDeleteValuation: (Long) -> Unit = {},
    val onDeleteAsset: () -> Unit = {},
)

@Composable
fun AssetDetailScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, viewModel: AssetDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.gone) { if (state.gone) onBack() }
    AssetDetailContent(
        state,
        AssetDetailActions(
            onBack = onBack,
            onEdit = { state.asset?.let { onEdit(it.id) } },
            onOpenSheet = viewModel::open,
            onAddValuation = viewModel::addValuation,
            onAddFlow = viewModel::addFlow,
            onDismissError = viewModel::dismissError,
            onDeleteFlow = { viewModel.deleteFlow(it) },
            onDeleteValuation = { viewModel.deleteValuation(it) },
            onDeleteAsset = { viewModel.deleteAsset() },
        ),
    )
}

@Composable
fun FlowKind.label(): String = stringResource(
    when (this) {
        FlowKind.INITIAL -> R.string.flow_initial
        FlowKind.CONTRIBUTION -> R.string.flow_contribution
        FlowKind.WITHDRAWAL -> R.string.flow_withdrawal
        FlowKind.PARTIAL_DISPOSAL -> R.string.flow_partial
        FlowKind.FULL_DISPOSAL -> R.string.flow_full_done
    },
)

@Composable
fun AssetDetailContent(state: AssetDetailUiState, actions: AssetDetailActions) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    var deleting by rememberSaveable { mutableStateOf(false) }
    val asset = state.asset
    DetailScaffold(
        title = asset?.name.orEmpty(),
        onBack = actions.onBack,
        actions = {
            IconButton(onClick = actions.onEdit) { Icon(Icons.Rounded.Edit, stringResource(R.string.asset_edit), tint = c.textSecondary) }
            IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.action_delete), tint = c.textSecondary) }
        },
    ) {
        val s = state.snapshot
        if (asset == null || s == null) return@DetailScaffold
        state.imageFile?.let { StoredImage(it, asset.name, Modifier.fillMaxWidth().height(180.dp).clip(FinanceShapes.medium)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(CategoryIcons.of(state.category?.icon ?: "category"))
            Spacer(Modifier.width(Spacing.m))
            Text(
                listOfNotNull(state.category?.displayName(), asset.institution).joinToString(" · "),
                style = FinanceTheme.type.body,
                color = c.textSecondary,
            )
        }
        FinanceCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.asset_value), style = FinanceTheme.type.caption, color = c.textSecondary)
            AmountDisplay(s.value, style = FinanceTheme.type.numericLarge)
            Text(
                when {
                    s.closed -> stringResource(R.string.asset_closed)
                    s.lastValuationDate != null -> stringResource(R.string.asset_last_valuation, formats.dates.date(s.lastValuationDate))
                    else -> stringResource(R.string.asset_no_valuation)
                },
                style = FinanceTheme.type.caption,
                color = c.textSecondary,
            )
            if (s.carriedForward) Text(stringResource(R.string.asset_carried), style = FinanceTheme.type.caption, color = c.textTertiary)
            Spacer(Modifier.height(Spacing.m))
            Line(stringResource(R.string.asset_invested_net), s.netInvested)
            Line(stringResource(R.string.asset_contributed), s.contributed)
            if (!s.withdrawn.isZero) Line(stringResource(R.string.asset_withdrawn), s.withdrawn)
            Line(stringResource(R.string.asset_gain), s.gain, AmountTone.AUTO, signed = true)
            s.simpleReturn?.let { r ->
                AdaptiveRow(
                    main = { Text(stringResource(R.string.asset_return), style = FinanceTheme.type.body, color = c.textSecondary) },
                    trailing = { Text(formats.money.formatPercent(r, signed = true), style = FinanceTheme.type.numeric, color = if (r.signum() < 0) c.negative else c.positive, maxLines = 1) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        state.converted?.let { conv ->
            FinanceCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.asset_in_display, state.display.symbol), style = FinanceTheme.type.caption, color = c.textSecondary)
                TotalLine(stringResource(R.string.asset_value), conv.value)
                TotalLine(stringResource(R.string.asset_contributed), conv.contributed)
                TotalLine(stringResource(R.string.asset_gain), conv.gain, AmountTone.AUTO)
                RateFooter(state.rate, Modifier.padding(top = Spacing.s))
            }
        }
        Text(stringResource(R.string.asset_method), style = FinanceTheme.type.caption, color = c.textTertiary)

        if (s.closed) {
            Text(stringResource(R.string.asset_closed_hint), style = FinanceTheme.type.caption, color = c.textSecondary)
        } else {
            PrimaryButton(stringResource(R.string.asset_update_value), onClick = { actions.onOpenSheet(AssetSheet.VALUATION) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SecondaryButton(stringResource(R.string.asset_contribute), onClick = { actions.onOpenSheet(AssetSheet.CONTRIBUTE) }, modifier = Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.asset_withdraw), onClick = { actions.onOpenSheet(AssetSheet.WITHDRAW) }, modifier = Modifier.weight(1f))
            }
        }

        if (state.series.size >= 2) {
            SectionHeader(stringResource(R.string.asset_chart), Modifier.padding(top = Spacing.s))
            FinanceCard(Modifier.fillMaxWidth()) {
                val dates = state.series.map { it.date }
                FinanceLineChart(
                    series = listOf(
                        LineSeries(state.series.mapIndexed { i, p -> i.toDouble() to p.invested.money.toBigDecimal().toDouble() }, c.chart[3]),
                        LineSeries(state.series.mapIndexed { i, p -> i.toDouble() to p.value.money.toBigDecimal().toDouble() }, c.accent, area = true),
                    ),
                    xLabel = { v -> dates.getOrNull(v.toInt())?.let(formats.dates::dayMonth).orEmpty() },
                    yLabel = { v -> formats.money.formatShort((v * 100).toLong(), asset.currency) },
                    summary = stringResource(R.string.asset_chart_summary),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    LegendDot(c.accent, stringResource(R.string.asset_value))
                    LegendDot(c.chart[3], stringResource(R.string.asset_invested_net))
                }
            }
        }

        if (state.valuations.isNotEmpty()) {
            SectionHeader(stringResource(R.string.asset_valuations), Modifier.padding(top = Spacing.s))
            state.valuations.forEach { row ->
                FinanceCard(Modifier.fillMaxWidth(), contentPadding = Spacing.m) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AdaptiveRow(
                            main = {
                                Column {
                                    Text(formats.dates.date(row.valuation.date), style = FinanceTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1)
                                    Text(stringResource(R.string.asset_valuation_row, formats.money.format(row.netInvested)), style = FinanceTheme.type.caption, color = c.textSecondary)
                                }
                            },
                            trailing = {
                                Column(horizontalAlignment = Alignment.End) {
                                    AmountDisplay(row.valuation.value)
                                    FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
                                        AmountDisplay(row.gain, style = FinanceTheme.type.caption.copy(fontFeatureSettings = "tnum"), signed = true, tone = AmountTone.AUTO)
                                        row.simpleReturn?.let { Text(" · " + formats.money.formatPercent(it, signed = true), style = FinanceTheme.type.caption, color = c.textSecondary, maxLines = 1) }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { actions.onDeleteValuation(row.valuation.id) }) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.action_delete), tint = c.textTertiary) }
                    }
                }
            }
        }

        SectionHeader(stringResource(R.string.asset_flows), Modifier.padding(top = Spacing.s))
        state.flows.forEach { flow -> AssetFlowRow(flow, actions.onDeleteFlow) }
    }

    val s = state.snapshot
    if (asset != null && s != null) {
        val minDate = state.flows.firstOrNull { it.kind == FlowKind.INITIAL }?.date
        when (state.sheet) {
            AssetSheet.VALUATION -> ValuationSheet(asset.currency, state.today, minDate, state.valuationDates, actions.onAddValuation) { actions.onOpenSheet(null) }
            AssetSheet.CONTRIBUTE -> FlowSheet(false, asset.currency, state.today, minDate, s.value, actions.onAddFlow, { actions.onOpenSheet(null) }, state.rates)
            AssetSheet.WITHDRAW -> FlowSheet(true, asset.currency, state.today, minDate, s.value, actions.onAddFlow, { actions.onOpenSheet(null) }, state.rates)
            null -> Unit
        }
    }
    ActionErrorDialog(state.error, actions.onDismissError)
    if (deleting) {
        ConfirmDialog(
            title = stringResource(R.string.asset_delete_title),
            message = stringResource(R.string.asset_delete_message),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { deleting = false; actions.onDeleteAsset() },
            onDismiss = { deleting = false },
            destructive = true,
        )
    }
}

@Composable
private fun Line(label: String, money: Money, tone: AmountTone = AmountTone.NEUTRAL, signed: Boolean = false) {
    AdaptiveRow(
        main = { Text(label, style = FinanceTheme.type.body, color = FinanceTheme.colors.textSecondary) },
        trailing = { AmountDisplay(money, tone = tone, signed = signed) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TotalLine(label: String, total: Total, tone: AmountTone = AmountTone.NEUTRAL) {
    AdaptiveRow(
        main = { Text(label, style = FinanceTheme.type.body, color = FinanceTheme.colors.textSecondary) },
        trailing = { AmountDisplay(total.money, tone = tone, signed = tone == AmountTone.AUTO, approximate = total.incomplete) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AssetFlowRow(flow: AssetFlow, onDelete: (Long) -> Unit) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    var confirmDelete by rememberSaveable(flow.id) { mutableStateOf(false) }
    val linked = flow.occurrenceId != null
    FinanceCard(Modifier.fillMaxWidth(), contentPadding = Spacing.m) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AdaptiveRow(
                main = {
                    Column {
                        Text(flow.kind.label(), style = FinanceTheme.type.bodyStrong, color = c.textPrimary)
                        val cash = if (linked) stringResource(if (flow.kind.inflow) R.string.flow_from_cash else R.string.flow_to_cash) else null
                        Text(listOfNotNull(formats.dates.date(flow.date), cash, flow.note).joinToString(" · "), style = FinanceTheme.type.caption, color = c.textSecondary)
                    }
                },
                trailing = { AmountDisplay(if (flow.kind.inflow) flow.amount else -flow.amount, signed = flow.kind.inflow) },
                modifier = Modifier.weight(1f),
            )
            if (flow.kind != FlowKind.INITIAL) {
                // A flow linked to cash takes its transfer with it: ask first.
                IconButton(onClick = { if (linked) confirmDelete = true else onDelete(flow.id) }) {
                    Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.action_delete), tint = c.textTertiary)
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.flow_delete_linked_title),
            message = stringResource(R.string.flow_delete_linked_message),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { confirmDelete = false; onDelete(flow.id) },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}
