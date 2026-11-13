package io.github.luannamorim.myfinance.ui.feature.asset

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.ui.common.AssetCategoryPicker
import io.github.luannamorim.myfinance.ui.common.StoredImage
import io.github.luannamorim.myfinance.ui.components.ChoiceRow
import io.github.luannamorim.myfinance.ui.components.CurrencyInput
import io.github.luannamorim.myfinance.ui.components.DateSelector
import io.github.luannamorim.myfinance.ui.components.DetailScaffold
import io.github.luannamorim.myfinance.ui.components.FinanceTextField
import io.github.luannamorim.myfinance.ui.components.PrimaryButton
import io.github.luannamorim.myfinance.ui.components.SecondaryButton
import io.github.luannamorim.myfinance.ui.components.TextAction
import io.github.luannamorim.myfinance.ui.theme.FinanceShapes
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing

data class AssetEditorActions(
    val onBack: () -> Unit = {},
    val onEdit: ((AssetEditorUiState) -> AssetEditorUiState) -> Unit = {},
    val onPickPhoto: () -> Unit = {},
    val onRemovePhoto: () -> Unit = {},
    val onSave: () -> Unit = {},
)

@Composable
fun AssetEditorScreen(onClose: () -> Unit, viewModel: AssetEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    // System photo picker: no storage permission needed.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::setPhoto) }
    AssetEditorContent(
        state,
        AssetEditorActions(
            onBack = onClose,
            onEdit = viewModel::edit,
            onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemovePhoto = viewModel::removePhoto,
            onSave = { viewModel.save() },
        ),
    )
}

@Composable
fun AssetEditorContent(state: AssetEditorUiState, actions: AssetEditorActions) {
    val c = FinanceTheme.colors
    DetailScaffold(
        title = stringResource(if (state.isNew) R.string.asset_new else R.string.asset_edit),
        onBack = actions.onBack,
        bottomBar = { PrimaryButton(stringResource(R.string.action_save), onClick = actions.onSave, modifier = Modifier.fillMaxWidth()) },
    ) {
        if (state.loading) return@DetailScaffold
        FinanceTextField(
            value = state.name,
            onValueChange = { v -> actions.onEdit { it.copy(name = v) } },
            label = stringResource(R.string.asset_name),
            placeholder = stringResource(R.string.asset_name_hint),
            isError = state.nameError,
            supportingText = if (state.nameError) stringResource(R.string.error_name_required) else null,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.asset_category), style = FinanceTheme.type.caption, color = if (state.categoryError) c.danger else c.textSecondary)
            AssetGroup.entries.forEach { group ->
                val list = state.categories.filter { it.group == group }
                if (list.isNotEmpty()) {
                    Text(
                        stringResource(if (group == AssetGroup.INVESTMENT) R.string.group_investment else R.string.group_personal),
                        style = FinanceTheme.type.overline,
                        color = c.textTertiary,
                    )
                    AssetCategoryPicker(list, state.categoryId, { id -> actions.onEdit { it.copy(categoryId = id) } })
                }
            }
            if (state.categoryError) Text(stringResource(R.string.error_category_required), style = FinanceTheme.type.caption, color = c.danger)
        }
        if (state.isNew) {
            ChoiceRow(stringResource(R.string.asset_currency), Currency.entries.map { it to it.symbol }, state.currency, { cur -> actions.onEdit { it.copy(currency = cur) } })
            CurrencyInput(
                digits = state.investedDigits,
                currency = state.currency,
                onDigits = { d -> actions.onEdit { it.copy(investedDigits = d) } },
                label = stringResource(R.string.asset_invested),
                supportingText = stringResource(R.string.asset_invested_hint),
            )
            CurrencyInput(
                digits = state.valueDigits,
                currency = state.currency,
                onDigits = { d -> actions.onEdit { it.copy(valueDigits = d) } },
                label = stringResource(R.string.asset_current_value),
            )
        } else {
            Text("${stringResource(R.string.asset_currency)}: ${state.currency.symbol} · ${stringResource(R.string.asset_currency_locked)}", style = FinanceTheme.type.caption, color = c.textSecondary)
        }
        DateSelector(stringResource(R.string.asset_acquired), state.acquisitionDate, { d -> actions.onEdit { it.copy(acquisitionDate = d) } }, maxDate = state.today)
        FinanceTextField(value = state.institution, onValueChange = { v -> actions.onEdit { it.copy(institution = v) } }, label = stringResource(R.string.asset_institution))
        FinanceTextField(
            value = state.quantity,
            onValueChange = { v -> actions.onEdit { it.copy(quantity = v) } },
            label = stringResource(R.string.asset_quantity),
            keyboardType = KeyboardType.Decimal,
            isError = state.quantityError,
            supportingText = if (state.quantityError) stringResource(R.string.asset_quantity_invalid) else null,
        )
        FinanceTextField(value = state.description, onValueChange = { v -> actions.onEdit { it.copy(description = v) } }, label = stringResource(R.string.asset_description))
        FinanceTextField(value = state.notes, onValueChange = { v -> actions.onEdit { it.copy(notes = v) } }, label = stringResource(R.string.asset_notes), singleLine = false, minLines = 2)
        Text(stringResource(R.string.asset_photo), style = FinanceTheme.type.caption, color = c.textSecondary)
        state.imageFile?.let { file ->
            StoredImage(file, stringResource(R.string.asset_photo), Modifier.fillMaxWidth().height(180.dp).clip(FinanceShapes.medium))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            SecondaryButton(stringResource(R.string.asset_photo_add), onClick = actions.onPickPhoto, icon = Icons.Rounded.AddAPhoto)
            if (state.imagePath != null) TextAction(stringResource(R.string.asset_photo_remove), onClick = actions.onRemovePhoto, color = c.textSecondary)
        }
    }
}
