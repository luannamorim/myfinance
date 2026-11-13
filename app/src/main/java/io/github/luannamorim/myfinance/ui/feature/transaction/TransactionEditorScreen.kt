package io.github.luannamorim.myfinance.ui.feature.transaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.core.format.AmountInput
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.RecurrenceUnit
import io.github.luannamorim.myfinance.ui.common.ActionErrorDialog
import io.github.luannamorim.myfinance.ui.common.CategoryPicker
import io.github.luannamorim.myfinance.ui.common.PresetOrder
import io.github.luannamorim.myfinance.ui.common.directionLabel
import io.github.luannamorim.myfinance.ui.common.label
import io.github.luannamorim.myfinance.ui.components.ChoiceRow
import io.github.luannamorim.myfinance.ui.components.ConfirmDialog
import io.github.luannamorim.myfinance.ui.components.CurrencyInput
import io.github.luannamorim.myfinance.ui.components.DateSelector
import io.github.luannamorim.myfinance.ui.components.DetailScaffold
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.FinanceChip
import io.github.luannamorim.myfinance.ui.components.FinanceTextField
import io.github.luannamorim.myfinance.ui.components.NumberStepper
import io.github.luannamorim.myfinance.ui.components.PrimaryButton
import io.github.luannamorim.myfinance.ui.components.SwitchRow
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing
import java.time.LocalDate

data class TransactionEditorActions(
    val onBack: () -> Unit = {},
    val onKind: (EntryKind) -> Unit = {},
    val onName: (String) -> Unit = {},
    val onDigits: (String) -> Unit = {},
    val onCurrency: (Currency) -> Unit = {},
    val onCategory: (Long) -> Unit = {},
    val onDate: (LocalDate) -> Unit = {},
    val onRepeat: (RepeatChoice) -> Unit = {},
    val onCustomInterval: (Int) -> Unit = {},
    val onCustomUnit: (RecurrenceUnit) -> Unit = {},
    val onEndDate: (LocalDate?) -> Unit = {},
    val onDescription: (String) -> Unit = {},
    val onAlreadySettled: (Boolean) -> Unit = {},
    val onActive: (Boolean) -> Unit = {},
    val onNextDueDate: (LocalDate) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onAsset: (Long) -> Unit = {},
    val onAssetDigits: (String) -> Unit = {},
    val onDismissError: () -> Unit = {},
)

@Composable
fun TransactionEditorScreen(onClose: () -> Unit, viewModel: TransactionEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    TransactionEditorContent(
        state,
        TransactionEditorActions(
            onBack = onClose,
            onKind = viewModel::setKind,
            onName = viewModel::setName,
            onDigits = viewModel::setDigits,
            onCurrency = viewModel::setCurrency,
            onCategory = viewModel::setCategory,
            onDate = viewModel::setDate,
            onRepeat = viewModel::setRepeat,
            onCustomInterval = viewModel::setCustomInterval,
            onCustomUnit = viewModel::setCustomUnit,
            onEndDate = viewModel::setEndDate,
            onDescription = viewModel::setDescription,
            onAlreadySettled = viewModel::setAlreadySettled,
            onActive = viewModel::setActive,
            onNextDueDate = viewModel::setNextDueDate,
            onSave = { viewModel.save() },
            onDelete = viewModel::delete,
            onAsset = viewModel::setAsset,
            onAssetDigits = viewModel::setAssetDigits,
            onDismissError = viewModel::dismissError,
        ),
    )
}

@Composable
fun TransactionEditorContent(state: TransactionEditorUiState, actions: TransactionEditorActions) {
    val c = FinanceTheme.colors
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(
        when (state.mode) {
            EditorMode.NEW -> R.string.editor_new_title
            EditorMode.TEMPLATE -> R.string.editor_edit_recurring_title
            EditorMode.STANDALONE -> R.string.editor_edit_title
            EditorMode.OCCURRENCE -> R.string.editor_edit_occurrence_title
        },
    )
    DetailScaffold(
        title = title,
        onBack = actions.onBack,
        actions = {
            if (state.mode == EditorMode.TEMPLATE || state.mode == EditorMode.STANDALONE) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.action_delete), tint = c.textSecondary)
                }
            }
        },
        bottomBar = { PrimaryButton(stringResource(R.string.action_save), onClick = actions.onSave, modifier = Modifier.fillMaxWidth()) },
    ) {
        if (state.loading) return@DetailScaffold
        val editableKind = (state.mode == EditorMode.NEW || state.mode == EditorMode.STANDALONE) && !state.kindLocked
        if (editableKind) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FinanceChip(stringResource(R.string.kind_expense), selected = state.kind == EntryKind.EXPENSE, onClick = { actions.onKind(EntryKind.EXPENSE) })
                FinanceChip(stringResource(R.string.kind_income), selected = state.kind == EntryKind.INCOME, onClick = { actions.onKind(EntryKind.INCOME) })
                // A done income or expense can't turn into a transfer (it would need its asset side).
                if (!state.settledAlready || state.isTransfer) {
                    FinanceChip(stringResource(R.string.kind_transfer), selected = state.isTransfer, onClick = { if (!state.isTransfer) actions.onKind(EntryKind.TRANSFER_OUT) })
                }
            }
        }
        if (state.isTransfer && state.mode != EditorMode.OCCURRENCE) {
            if (state.kindLocked) {
                Text(stringResource(R.string.editor_transfer_locked, state.kind.directionLabel(), state.target?.name.orEmpty()), style = FinanceTheme.type.caption, color = c.textSecondary)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FinanceChip(EntryKind.TRANSFER_OUT.directionLabel(), selected = state.kind == EntryKind.TRANSFER_OUT, onClick = { actions.onKind(EntryKind.TRANSFER_OUT) })
                    FinanceChip(EntryKind.TRANSFER_IN.directionLabel(), selected = state.kind == EntryKind.TRANSFER_IN, onClick = { actions.onKind(EntryKind.TRANSFER_IN) })
                }
                Text(
                    stringResource(if (state.kind == EntryKind.TRANSFER_OUT) R.string.editor_transfer_out_hint else R.string.editor_transfer_in_hint),
                    style = FinanceTheme.type.caption,
                    color = c.textSecondary,
                )
            }
        }
        CurrencyInput(
            digits = state.digits,
            currency = state.currency,
            onDigits = actions.onDigits,
            label = stringResource(if (state.isRecurring || state.mode == EditorMode.OCCURRENCE) R.string.editor_expected_amount else R.string.editor_amount),
            onCurrency = if (state.mode == EditorMode.OCCURRENCE) null else actions.onCurrency,
            isError = state.amountError,
            supportingText = if (state.amountError) stringResource(R.string.error_amount_required) else null,
        )
        FinanceTextField(
            value = state.name,
            onValueChange = actions.onName,
            label = stringResource(if (state.isTransfer && state.mode != EditorMode.OCCURRENCE) R.string.editor_name_optional else R.string.editor_name),
            placeholder = if (state.isTransfer) state.target?.name ?: stringResource(R.string.editor_transfer_name_hint) else stringResource(R.string.editor_name_hint),
            isError = state.nameError,
            supportingText = if (state.nameError) stringResource(R.string.error_name_required) else null,
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        )
        if (state.mode != EditorMode.OCCURRENCE && state.isTransfer) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.editor_asset), style = FinanceTheme.type.caption, color = if (state.assetError && state.assetId == null) c.danger else c.textSecondary)
                if (state.assets.isEmpty()) {
                    Text(stringResource(R.string.editor_no_assets), style = FinanceTheme.type.caption, color = c.textSecondary)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        state.assets.forEach { target ->
                            FinanceChip(
                                target.name,
                                selected = target.assetId == state.assetId,
                                onClick = { if (!state.kindLocked) actions.onAsset(target.assetId) },
                            )
                        }
                    }
                }
                if (state.assetError && state.assetId == null) Text(stringResource(R.string.error_asset_required), style = FinanceTheme.type.caption, color = c.danger)
            }
        } else if (state.mode != EditorMode.OCCURRENCE) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.editor_category), style = FinanceTheme.type.caption, color = if (state.categoryError) c.danger else c.textSecondary)
                CategoryPicker(state.categories, state.categoryId, actions.onCategory)
                if (state.categoryError) Text(stringResource(R.string.error_category_required), style = FinanceTheme.type.caption, color = c.danger)
            }
        }
        when (state.mode) {
            EditorMode.TEMPLATE -> state.nextDueDate?.let {
                DateSelector(stringResource(R.string.editor_next_due), it, actions.onNextDueDate, minDate = state.today)
            }
            EditorMode.NEW -> DateSelector(
                stringResource(if (state.isRecurring) R.string.editor_first_due else R.string.editor_date),
                state.date,
                actions.onDate,
                // A transfer already done can't predate the asset's first investment.
                minDate = state.target?.acquired?.takeIf { state.isTransfer && state.canSettle && state.alreadySettled },
            )
            EditorMode.STANDALONE -> DateSelector(stringResource(R.string.editor_date), state.date, actions.onDate)
            EditorMode.OCCURRENCE -> DateSelector(stringResource(R.string.editor_due_date), state.date, actions.onDate)
        }
        if (state.mode == EditorMode.NEW || state.mode == EditorMode.TEMPLATE) RepeatSection(state, actions)
        if (state.canSettle) {
            SwitchRow(
                stringResource(
                    when (state.kind) {
                        EntryKind.INCOME -> R.string.editor_already_received
                        EntryKind.EXPENSE -> R.string.editor_already_paid
                        EntryKind.TRANSFER_OUT, EntryKind.TRANSFER_IN -> R.string.editor_already_transferred
                    },
                ),
                stringResource(R.string.editor_already_hint),
                state.alreadySettled,
                actions.onAlreadySettled,
            )
        }
        val target = state.target
        if (state.needsAssetAmount && target != null) {
            val assetMinor = AmountInput.minorOf(state.assetAmountDigits) ?: 0
            CurrencyInput(
                digits = state.assetAmountDigits,
                currency = target.currency,
                onDigits = actions.onAssetDigits,
                label = stringResource(if (state.kind == EntryKind.TRANSFER_OUT) R.string.transfer_asset_received else R.string.transfer_asset_gave, target.currency.symbol),
                isError = state.assetError && assetMinor <= 0,
                supportingText = stringResource(R.string.transfer_asset_amount_hint),
            )
        }
        if (state.mode != EditorMode.OCCURRENCE) {
            FinanceTextField(
                value = state.description,
                onValueChange = actions.onDescription,
                label = stringResource(R.string.editor_description),
                singleLine = false,
                minLines = 2,
            )
        }
        if (state.mode == EditorMode.TEMPLATE) {
            SwitchRow(stringResource(R.string.editor_active), stringResource(R.string.editor_active_hint), state.active, actions.onActive)
            FinanceCard(Modifier.fillMaxWidth(), color = c.surfaceRaised) {
                Text(stringResource(R.string.editor_template_note), style = FinanceTheme.type.caption, color = c.textSecondary)
                if (state.customizedCount > 0) {
                    Text(
                        pluralStringResource(R.plurals.editor_customized_note, state.customizedCount, state.customizedCount),
                        style = FinanceTheme.type.caption,
                        color = c.warning,
                    )
                }
            }
        }
    }
    ActionErrorDialog(state.error, actions.onDismissError)
    if (confirmDelete) {
        val recurring = state.mode == EditorMode.TEMPLATE
        ConfirmDialog(
            title = stringResource(if (recurring) R.string.editor_delete_recurring_title else R.string.editor_delete_title),
            message = stringResource(if (recurring) R.string.editor_delete_recurring_message else R.string.editor_delete_message),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { confirmDelete = false; actions.onDelete() },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

@Composable
private fun RepeatSection(state: TransactionEditorUiState, actions: TransactionEditorActions) {
    val options = buildList {
        if (state.mode == EditorMode.NEW) add(RepeatChoice.Never to stringResource(R.string.repeat_never))
        PresetOrder.forEach { add(RepeatChoice.Preset(it) to it.label()) }
        add(RepeatChoice.Custom to stringResource(R.string.repeat_custom))
    }
    ChoiceRow(stringResource(R.string.editor_repeat), options, state.repeat, actions.onRepeat)
    if (state.repeat == RepeatChoice.Custom) {
        NumberStepper(
            value = state.customInterval.toString(),
            label = stringResource(R.string.editor_every),
            onDecrement = { actions.onCustomInterval(state.customInterval - 1) },
            onIncrement = { actions.onCustomInterval(state.customInterval + 1) },
        )
        ChoiceRow("", RecurrenceUnit.entries.map { it to it.label() }, state.customUnit, actions.onCustomUnit)
    }
    if (state.isRecurring) {
        ChoiceRow(
            stringResource(R.string.editor_ends),
            listOf(false to stringResource(R.string.editor_ends_never), true to stringResource(R.string.editor_ends_on)),
            selected = state.endDate != null,
            onSelect = { on -> actions.onEndDate(if (on) state.endDate ?: state.date.plusYears(1) else null) },
        )
        state.endDate?.let { DateSelector(stringResource(R.string.editor_end_date), it, { d -> actions.onEndDate(d) }, minDate = state.date) }
    }
}
