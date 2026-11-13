package io.github.luannamorim.myfinance.ui.feature.transaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.ui.common.CategoryIcons
import io.github.luannamorim.myfinance.ui.common.ConfirmSheet
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.common.StatusChip
import io.github.luannamorim.myfinance.ui.common.displayName
import io.github.luannamorim.myfinance.ui.common.label
import io.github.luannamorim.myfinance.ui.components.AmountDisplay
import io.github.luannamorim.myfinance.ui.components.AmountTone
import io.github.luannamorim.myfinance.ui.components.ConfirmDialog
import io.github.luannamorim.myfinance.ui.components.DetailScaffold
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.PrimaryButton
import io.github.luannamorim.myfinance.ui.components.SecondaryButton
import io.github.luannamorim.myfinance.ui.components.TextAction
import io.github.luannamorim.myfinance.ui.feature.settings.CategoryBadge
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing
import java.time.LocalDate

data class OccurrenceDetailActions(
    val onBack: () -> Unit = {},
    val onStartConfirm: () -> Unit = {},
    val onDismissConfirm: () -> Unit = {},
    val onConfirm: (Money, LocalDate, String?) -> Unit = { _, _, _ -> },
    val onUnconfirm: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onRestore: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onEditRecurring: (Long) -> Unit = {},
)

@Composable
fun OccurrenceDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onEditRecurring: (Long) -> Unit,
    viewModel: OccurrenceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.gone) { if (state.gone) onBack() }
    OccurrenceDetailContent(
        state,
        OccurrenceDetailActions(
            onBack = onBack,
            onStartConfirm = viewModel::startConfirm,
            onDismissConfirm = viewModel::dismissConfirm,
            onConfirm = viewModel::confirm,
            onUnconfirm = { viewModel.unconfirm() },
            onCancel = { viewModel.cancel() },
            onRestore = { viewModel.restore() },
            onDelete = { viewModel.delete() },
            onEdit = { state.occurrence?.let { onEdit(it.id) } },
            onEditRecurring = onEditRecurring,
        ),
    )
}

private enum class PendingDialog { UNCONFIRM, CANCEL, DELETE }

@Composable
fun OccurrenceDetailContent(state: OccurrenceDetailUiState, actions: OccurrenceDetailActions) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    var dialog by rememberSaveable { mutableStateOf<PendingDialog?>(null) }
    val o = state.occurrence
    DetailScaffold(
        title = stringResource(R.string.detail_title),
        onBack = actions.onBack,
        actions = {
            if (o?.standalone == true) {
                IconButton(onClick = { dialog = PendingDialog.DELETE }) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.action_delete), tint = c.textSecondary) }
            }
        },
    ) {
        if (o == null) return@DetailScaffold
        val income = o.kind == EntryKind.INCOME
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(CategoryIcons.of(state.category?.icon ?: "category"))
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(o.name, style = FinanceTheme.type.headline, color = c.textPrimary)
                Text(state.category?.displayName().orEmpty(), style = FinanceTheme.type.caption, color = c.textSecondary)
            }
            StatusChip(state.status)
        }
        FinanceCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.detail_expected), style = FinanceTheme.type.caption, color = c.textSecondary)
            AmountDisplay(if (income) o.expected else -o.expected, style = FinanceTheme.type.numericLarge, signed = income)
            Text("${stringResource(R.string.detail_due)} ${formats.dates.date(o.dueDate)}", style = FinanceTheme.type.caption, color = c.textSecondary)
            if (o.state == OccurrenceState.CONFIRMED && o.actual != null && o.actualDate != null) {
                Spacer(Modifier.height(Spacing.l))
                Text(
                    stringResource(if (income) R.string.detail_actual_received else R.string.detail_actual_paid) + " · " + formats.dates.date(o.actualDate),
                    style = FinanceTheme.type.caption,
                    color = c.textSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                AmountDisplay(if (income) o.actual else -o.actual, style = FinanceTheme.type.numericLarge, signed = income)
                state.difference?.takeIf { !it.isZero }?.let { diff ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.detail_difference) + " ", style = FinanceTheme.type.caption, color = c.textSecondary)
                        // Paying more than expected (or receiving less) is worse: red; the opposite is green.
                        val good = if (income) !diff.isNegative else diff.isNegative
                        AmountDisplay(diff, style = FinanceTheme.type.numeric, signed = true, tone = if (good) AmountTone.POSITIVE else AmountTone.NEGATIVE)
                    }
                }
            }
        }
        o.note?.let { Field(stringResource(R.string.detail_note), it) }
        o.description?.let { Field(stringResource(R.string.detail_description), it) }
        state.template?.let { t ->
            FinanceCard(onClick = { actions.onEditRecurring(t.id) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.detail_recurring), style = FinanceTheme.type.bodyStrong, color = c.textPrimary)
                Text("${t.rule.label()} · ${stringResource(R.string.detail_edit_recurring)}", style = FinanceTheme.type.caption, color = c.textSecondary)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            when (o.state) {
                OccurrenceState.PENDING -> {
                    PrimaryButton(
                        stringResource(if (income) R.string.confirm_receipt else R.string.confirm_payment),
                        onClick = actions.onStartConfirm,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(
                        stringResource(if (o.standalone) R.string.detail_edit else R.string.detail_edit_this),
                        onClick = actions.onEdit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextAction(stringResource(R.string.detail_cancel), onClick = { dialog = PendingDialog.CANCEL }, color = c.textSecondary)
                }
                OccurrenceState.CONFIRMED -> {
                    SecondaryButton(
                        stringResource(if (income) R.string.detail_mark_unreceived else R.string.detail_mark_unpaid),
                        onClick = { dialog = PendingDialog.UNCONFIRM },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (o.standalone) SecondaryButton(stringResource(R.string.detail_edit), onClick = actions.onEdit, modifier = Modifier.fillMaxWidth())
                }
                OccurrenceState.CANCELLED -> SecondaryButton(stringResource(R.string.detail_restore), onClick = actions.onRestore, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (state.confirming && o != null) {
        ConfirmSheet(o, state.today, onConfirm = actions.onConfirm, onDismiss = actions.onDismissConfirm)
    }
    when (dialog) {
        PendingDialog.UNCONFIRM -> ConfirmDialog(
            title = stringResource(R.string.detail_unconfirm_title),
            message = stringResource(R.string.detail_unconfirm_message),
            confirmText = stringResource(if (o?.kind == EntryKind.INCOME) R.string.detail_mark_unreceived else R.string.detail_mark_unpaid),
            onConfirm = { dialog = null; actions.onUnconfirm() },
            onDismiss = { dialog = null },
        )
        PendingDialog.CANCEL -> ConfirmDialog(
            title = stringResource(R.string.detail_cancel_title),
            message = stringResource(R.string.detail_cancel_message),
            confirmText = stringResource(R.string.detail_cancel),
            dismissText = stringResource(R.string.detail_keep),
            onConfirm = { dialog = null; actions.onCancel() },
            onDismiss = { dialog = null },
            destructive = true,
        )
        PendingDialog.DELETE -> ConfirmDialog(
            title = stringResource(R.string.editor_delete_title),
            message = stringResource(R.string.editor_delete_message),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { dialog = null; actions.onDelete() },
            onDismiss = { dialog = null },
            destructive = true,
        )
        null -> Unit
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = FinanceTheme.type.caption, color = FinanceTheme.colors.textSecondary)
        Text(value, style = FinanceTheme.type.body, color = FinanceTheme.colors.textPrimary)
    }
}
