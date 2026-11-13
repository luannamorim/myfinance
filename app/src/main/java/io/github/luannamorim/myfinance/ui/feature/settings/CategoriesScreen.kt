package io.github.luannamorim.myfinance.ui.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.ui.common.CategoryIcons
import io.github.luannamorim.myfinance.ui.common.seedNameRes
import io.github.luannamorim.myfinance.ui.components.ChoiceRow
import io.github.luannamorim.myfinance.ui.components.DetailScaffold
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.FinanceChip
import io.github.luannamorim.myfinance.ui.components.FinanceTextField
import io.github.luannamorim.myfinance.ui.components.PrimaryButton
import io.github.luannamorim.myfinance.ui.components.SecondaryButton
import io.github.luannamorim.myfinance.ui.components.SectionHeader
import io.github.luannamorim.myfinance.ui.components.TextAction
import io.github.luannamorim.myfinance.ui.navigation.CategoryScope
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing

data class CategoriesActions(
    val onBack: () -> Unit = {},
    val onScope: (CategoryScope) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onEdit: (CategoryItem, String) -> Unit = { _, _ -> },
    val onEditChange: ((CategoryEdit) -> CategoryEdit) -> Unit = {},
    val onSave: () -> Unit = {},
    val onToggleArchived: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onDismissEditor: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
)

@Composable
fun CategoriesScreen(onBack: () -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CategoriesContent(
        state,
        CategoriesActions(
            onBack = onBack,
            onScope = viewModel::setScope,
            onAdd = viewModel::startNew,
            onEdit = viewModel::startEdit,
            onEditChange = viewModel::edit,
            onSave = viewModel::save,
            onToggleArchived = viewModel::toggleArchived,
            onDelete = viewModel::delete,
            onDismissEditor = viewModel::dismissEditor,
            onDismissMessage = viewModel::dismissMessage,
        ),
    )
}

@Composable
private fun CategoryItem.label(): String = name ?: seedNameRes(seedKey)?.let { stringResource(it) } ?: ""

@Composable
fun CategoriesContent(state: CategoriesUiState, actions: CategoriesActions) {
    DetailScaffold(
        title = stringResource(R.string.categories_title),
        onBack = actions.onBack,
        actions = {
            IconButton(onClick = actions.onAdd) { Icon(Icons.Rounded.Add, stringResource(R.string.categories_add), tint = FinanceTheme.colors.textPrimary) }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            listOf(CategoryScope.EXPENSE to R.string.categories_expense, CategoryScope.INCOME to R.string.categories_income, CategoryScope.ASSET to R.string.categories_assets)
                .forEach { (scope, label) -> FinanceChip(stringResource(label), selected = state.scope == scope, onClick = { actions.onScope(scope) }) }
        }
        if (state.scope == CategoryScope.ASSET) {
            AssetGroup.entries.forEach { group ->
                val items = state.active.filter { it.group == group }
                if (items.isNotEmpty()) {
                    SectionHeader(stringResource(if (group == AssetGroup.INVESTMENT) R.string.group_investment else R.string.group_personal))
                    items.forEach { CategoryRow(it, actions) }
                }
            }
        } else {
            state.active.forEach { CategoryRow(it, actions) }
        }
        if (state.archived.isNotEmpty()) {
            SectionHeader(stringResource(R.string.categories_archived), Modifier.padding(top = Spacing.s))
            state.archived.forEach { CategoryRow(it, actions) }
        }
    }
    state.editing?.let { CategoryEditor(it, state.scope, actions) }
    if (state.message == CategoryMessage.IN_USE) {
        AlertDialog(
            onDismissRequest = actions.onDismissMessage,
            title = { Text(stringResource(R.string.category_in_use_title)) },
            text = { Text(stringResource(R.string.category_in_use_message)) },
            confirmButton = { TextAction(stringResource(R.string.action_ok), onClick = actions.onDismissMessage) },
            containerColor = FinanceTheme.colors.surfaceRaised,
        )
    }
}

@Composable
private fun CategoryRow(item: CategoryItem, actions: CategoriesActions) {
    val c = FinanceTheme.colors
    val label = item.label()
    FinanceCard(onClick = { actions.onEdit(item, label) }, modifier = Modifier.fillMaxWidth(), contentPadding = Spacing.m) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(CategoryIcons.of(item.icon), dimmed = item.archived)
            Spacer(Modifier.width(Spacing.m))
            Text(label, style = FinanceTheme.type.bodyStrong, color = if (item.archived) c.textTertiary else c.textPrimary)
        }
    }
}

/** A category icon in a neutral circle (icons are never coloured). */
@Composable
fun CategoryBadge(icon: ImageVector, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val c = FinanceTheme.colors
    Box(modifier.size(36.dp).background(c.surfaceRaised, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = if (dimmed) c.textTertiary else c.textSecondary, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditor(edit: CategoryEdit, scope: CategoryScope, actions: CategoriesActions) {
    val c = FinanceTheme.colors
    ModalBottomSheet(
        onDismissRequest = actions.onDismissEditor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceRaised,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(
                stringResource(if (edit.id == 0L) R.string.categories_add else R.string.categories_edit),
                style = FinanceTheme.type.headline,
                color = c.textPrimary,
            )
            FinanceTextField(
                value = edit.name,
                onValueChange = { v -> actions.onEditChange { it.copy(name = v, nameError = false) } },
                label = stringResource(R.string.category_name),
                isError = edit.nameError,
                supportingText = if (edit.nameError) stringResource(R.string.error_name_required) else null,
            )
            if (scope == CategoryScope.ASSET && edit.id == 0L) {
                ChoiceRow(
                    stringResource(R.string.category_group),
                    listOf(AssetGroup.INVESTMENT to stringResource(R.string.group_investment), AssetGroup.PERSONAL to stringResource(R.string.group_personal)),
                    selected = edit.group ?: AssetGroup.INVESTMENT,
                    onSelect = { g -> actions.onEditChange { it.copy(group = g) } },
                )
            }
            Text(stringResource(R.string.category_icon), style = FinanceTheme.type.caption, color = c.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                CategoryIcons.all.forEach { (key, icon) ->
                    val selected = key == edit.icon
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(if (selected) c.textPrimary else c.surface, CircleShape)
                            .border(1.dp, if (selected) c.textPrimary else c.outline, CircleShape)
                            .clickable(role = Role.RadioButton) { actions.onEditChange { it.copy(icon = key) } }
                            .semantics { this.selected = selected; contentDescription = key.replace('_', ' ') },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = if (selected) c.background else c.textSecondary, modifier = Modifier.size(22.dp))
                    }
                }
            }
            PrimaryButton(stringResource(R.string.action_save), onClick = actions.onSave, modifier = Modifier.fillMaxWidth())
            if (edit.id != 0L) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    SecondaryButton(stringResource(if (edit.archived) R.string.action_unarchive else R.string.action_archive), onClick = actions.onToggleArchived)
                    TextAction(stringResource(R.string.action_delete), onClick = actions.onDelete, color = c.danger)
                }
                Text(stringResource(R.string.category_archive_hint), style = FinanceTheme.type.caption, color = c.textSecondary)
            }
        }
    }
}
