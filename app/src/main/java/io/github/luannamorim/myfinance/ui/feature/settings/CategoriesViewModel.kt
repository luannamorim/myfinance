package io.github.luannamorim.myfinance.ui.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.luannamorim.myfinance.data.repository.CategoryRepository
import io.github.luannamorim.myfinance.data.repository.DeleteResult
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.ui.navigation.CategoriesRoute
import io.github.luannamorim.myfinance.ui.navigation.CategoryScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A category row, cash or asset. [name] is null for a predefined category never renamed. */
data class CategoryItem(
    val id: Long,
    val name: String?,
    val seedKey: String?,
    val icon: String,
    val archived: Boolean,
    val group: AssetGroup? = null,
)

/** The add/edit sheet. [id] 0 means a new category. */
data class CategoryEdit(val id: Long, val name: String, val icon: String, val group: AssetGroup?, val archived: Boolean, val nameError: Boolean = false)

enum class CategoryMessage { IN_USE }

data class CategoriesUiState(
    val loading: Boolean = true,
    val scope: CategoryScope = CategoryScope.EXPENSE,
    val active: List<CategoryItem> = emptyList(),
    val archived: List<CategoryItem> = emptyList(),
    val editing: CategoryEdit? = null,
    val message: CategoryMessage? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: CategoryRepository,
) : ViewModel() {

    private val scope = MutableStateFlow(savedStateHandle.toRoute<CategoriesRoute>().scope)
    private val local = MutableStateFlow(CategoriesUiState())

    private val items = scope.flatMapLatest { s ->
        when (s) {
            CategoryScope.INCOME -> repo.observe(EntryKind.INCOME, includeArchived = true).map { l -> l.map { CategoryItem(it.id, it.name, it.seedKey, it.icon, it.archived) } }
            CategoryScope.EXPENSE -> repo.observe(EntryKind.EXPENSE, includeArchived = true).map { l -> l.map { CategoryItem(it.id, it.name, it.seedKey, it.icon, it.archived) } }
            CategoryScope.ASSET -> repo.observeAssetCategories(includeArchived = true).map { l -> l.map { CategoryItem(it.id, it.name, it.seedKey, it.icon, it.archived, it.group) } }
        }
    }

    val state: StateFlow<CategoriesUiState> = combine(scope, items, local) { s, list, l ->
        l.copy(loading = false, scope = s, active = list.filterNot { it.archived }, archived = list.filter { it.archived })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun setScope(value: CategoryScope) {
        scope.value = value
        local.update { it.copy(editing = null) }
    }

    fun startNew() = local.update {
        val group = if (scope.value == CategoryScope.ASSET) AssetGroup.INVESTMENT else null
        it.copy(editing = CategoryEdit(0, "", "category", group, archived = false))
    }

    /** [displayName] is the localized name for predefined categories, resolved by the screen. */
    fun startEdit(item: CategoryItem, displayName: String) = local.update {
        it.copy(editing = CategoryEdit(item.id, displayName, item.icon, item.group, item.archived))
    }

    fun edit(transform: (CategoryEdit) -> CategoryEdit) = local.update { s -> s.copy(editing = s.editing?.let(transform)) }

    fun dismissEditor() = local.update { it.copy(editing = null) }

    fun dismissMessage() = local.update { it.copy(message = null) }

    fun save() {
        val edit = local.value.editing ?: return
        if (edit.name.isBlank()) {
            local.update { it.copy(editing = edit.copy(nameError = true)) }
            return
        }
        viewModelScope.launch {
            when (scope.value) {
                CategoryScope.ASSET ->
                    if (edit.id == 0L) repo.createAssetCategory(edit.group ?: AssetGroup.INVESTMENT, edit.name, edit.icon)
                    else repo.updateAssetCategory(edit.id, edit.name, edit.icon)
                CategoryScope.INCOME, CategoryScope.EXPENSE -> {
                    val kind = if (scope.value == CategoryScope.INCOME) EntryKind.INCOME else EntryKind.EXPENSE
                    if (edit.id == 0L) repo.create(kind, edit.name, edit.icon) else repo.update(edit.id, edit.name, edit.icon)
                }
            }
            local.update { it.copy(editing = null) }
        }
    }

    fun toggleArchived() {
        val edit = local.value.editing ?: return
        viewModelScope.launch {
            if (scope.value == CategoryScope.ASSET) repo.setAssetCategoryArchived(edit.id, !edit.archived)
            else repo.setArchived(edit.id, !edit.archived)
            local.update { it.copy(editing = null) }
        }
    }

    fun delete() {
        val edit = local.value.editing ?: return
        viewModelScope.launch {
            val result = if (scope.value == CategoryScope.ASSET) repo.deleteAssetCategory(edit.id) else repo.delete(edit.id)
            local.update { it.copy(editing = null, message = if (result == DeleteResult.IN_USE) CategoryMessage.IN_USE else null) }
        }
    }

    val currentScope: StateFlow<CategoryScope> = scope.asStateFlow()
}
