package io.github.luannamorim.myfinance.ui.feature.asset

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.core.format.AmountInput
import io.github.luannamorim.myfinance.core.format.DecimalInput
import io.github.luannamorim.myfinance.domain.market.Ticker
import io.github.luannamorim.myfinance.domain.market.TickerMatch
import io.github.luannamorim.myfinance.domain.market.TickerType
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.ui.common.ActionErrorDialog
import io.github.luannamorim.myfinance.ui.common.AssetCategoryPicker
import io.github.luannamorim.myfinance.ui.common.AssetIcon
import io.github.luannamorim.myfinance.ui.common.CashLinkFields
import io.github.luannamorim.myfinance.ui.common.CategoryIcons
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.common.SplitNote
import io.github.luannamorim.myfinance.ui.common.icon
import io.github.luannamorim.myfinance.ui.common.label
import io.github.luannamorim.myfinance.ui.common.ratio
import io.github.luannamorim.myfinance.ui.common.unitsLabel
import io.github.luannamorim.myfinance.ui.components.ChoiceRow
import io.github.luannamorim.myfinance.ui.components.CurrencyInput
import io.github.luannamorim.myfinance.ui.components.DateSelector
import io.github.luannamorim.myfinance.ui.components.DetailScaffold
import io.github.luannamorim.myfinance.ui.components.FinanceCard
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
    val onPickLogo: () -> Unit = {},
    val onRemoveLogo: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onPickTicker: (TickerMatch) -> Unit = {},
    val onClearTicker: () -> Unit = {},
)

@Composable
fun AssetEditorScreen(onClose: () -> Unit, viewModel: AssetEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    // System photo picker: no storage permission needed.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::setLogo) }
    AssetEditorContent(
        state,
        AssetEditorActions(
            onBack = onClose,
            onEdit = viewModel::edit,
            onPickLogo = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemoveLogo = viewModel::removeLogo,
            onSave = { viewModel.save() },
            onDismissError = viewModel::dismissError,
            onQuery = viewModel::onQuery,
            onPickTicker = viewModel::pickTicker,
            onClearTicker = viewModel::clearTicker,
        ),
    )
}

@Composable
fun AssetEditorContent(state: AssetEditorUiState, actions: AssetEditorActions) {
    DetailScaffold(
        title = stringResource(if (state.isNew) R.string.asset_new else R.string.asset_edit),
        onBack = actions.onBack,
        bottomBar = { PrimaryButton(stringResource(R.string.action_save), onClick = actions.onSave, modifier = Modifier.fillMaxWidth()) },
    ) {
        if (state.loading) return@DetailScaffold
        if (state.isNew) {
            ChoiceRow(
                stringResource(R.string.asset_mode),
                listOf(false to stringResource(R.string.asset_mode_manual), true to stringResource(R.string.asset_mode_quote)),
                state.tracked,
                { v -> actions.onEdit { it.copy(tracked = v) } },
            )
        }
        if (state.isNew && state.tracked) TrackedFields(state, actions) else ManualFields(state, actions)
    }
    ActionErrorDialog(state.error, actions.onDismissError)
}

@Composable
private fun ColumnScope.ManualFields(state: AssetEditorUiState, actions: AssetEditorActions) {
    val c = FinanceTheme.colors
    NameField(state, actions)
    CategoryField(state, actions)
    if (state.isNew) {
        ChoiceRow(stringResource(R.string.asset_currency), Currency.entries.map { it to it.symbol }, state.currency, { cur -> actions.onEdit { it.copy(currency = cur) } })
        CurrencyInput(
            digits = state.investedDigits,
            currency = state.currency,
            onDigits = { d -> actions.onEdit { it.copy(investedDigits = d) } },
            label = stringResource(R.string.asset_invested),
            supportingText = stringResource(R.string.asset_invested_hint),
        )
        val invested = AmountInput.minorOf(state.investedDigits) ?: 0
        CashFields(state, actions, Money(invested, state.currency).takeIf { invested > 0 })
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
    InstitutionField(state, actions)
    if (!state.editingTracked) {
        FinanceTextField(
            value = state.quantity,
            onValueChange = { v -> actions.onEdit { it.copy(quantity = v) } },
            label = stringResource(R.string.asset_quantity),
            keyboardType = KeyboardType.Decimal,
            isError = state.quantityError,
            supportingText = if (state.quantityError) stringResource(R.string.asset_quantity_invalid) else null,
        )
    }
    DescriptionField(state, actions)
    NotesAndLogo(state, actions)
}

/** "From quote": ticker search, then quantity, date and price per unit in USD; today's value from the last close. */
@Composable
private fun ColumnScope.TrackedFields(state: AssetEditorUiState, actions: AssetEditorActions) {
    val ticker = state.ticker
    if (ticker == null) {
        TickerSearch(state, actions)
        return
    }
    PickedTicker(ticker, actions.onClearTicker)
    val example = DecimalInput.example(state.numberFormat)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
        FinanceTextField(
            value = state.quantity,
            onValueChange = { v -> actions.onEdit { it.copy(quantity = v) } },
            label = stringResource(R.string.asset_shares),
            modifier = Modifier.weight(1f),
            keyboardType = KeyboardType.Decimal,
            isError = state.quantityError,
            supportingText = if (state.quantityError) stringResource(R.string.asset_number_invalid, example) else null,
        )
        DateSelector(
            stringResource(R.string.asset_acquired),
            state.acquisitionDate,
            { d -> actions.onEdit { it.copy(acquisitionDate = d) } },
            modifier = Modifier.weight(1f).padding(top = Spacing.s),
            maxDate = state.today,
        )
    }
    val coin = Ticker.unitCode(ticker.symbol)
    val money = LocalFormats.current.money
    val priceInvalid = DecimalInput.parse(state.priceText, state.numberFormat).isFailure
    FinanceTextField(
        value = state.priceText,
        onValueChange = { v -> actions.onEdit { it.copy(priceText = v) } },
        label = if (coin != null) stringResource(R.string.asset_price_per_coin, coin) else stringResource(R.string.asset_price_per_share),
        keyboardType = KeyboardType.Decimal,
        prefix = Currency.USD.symbol + " ",
        isError = state.priceError,
        supportingText = when {
            state.priceError && priceInvalid -> stringResource(R.string.asset_number_invalid, example)
            state.priceError -> stringResource(R.string.asset_price_required)
            else -> state.cost?.let { stringResource(R.string.asset_total_paid, money.format(it)) }
        },
    )
    TodayBlock(state, ticker.symbol)
    CashFields(state, actions, state.cost)
    NameField(state, actions)
    CategoryField(state, actions)
    InstitutionField(state, actions)
    DescriptionField(state, actions)
    NotesAndLogo(state, actions)
}

@Composable
private fun TickerSearch(state: AssetEditorUiState, actions: AssetEditorActions) {
    val c = FinanceTheme.colors
    FinanceTextField(
        value = state.query,
        onValueChange = actions.onQuery,
        label = stringResource(R.string.asset_ticker),
        placeholder = stringResource(R.string.asset_ticker_hint),
        capitalization = KeyboardCapitalization.Characters,
        isError = state.searchError || state.tickerError,
        supportingText = when {
            state.searchError -> stringResource(R.string.asset_ticker_search_error)
            state.tickerError -> stringResource(R.string.asset_ticker_required)
            else -> null
        },
    )
    if (state.matches.isEmpty()) return
    FinanceCard(Modifier.fillMaxWidth(), contentPadding = Spacing.xs) {
        state.matches.forEach { match ->
            Row(
                Modifier.fillMaxWidth().clip(FinanceShapes.small).clickable { actions.onPickTicker(match) }.padding(horizontal = Spacing.m, vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(match.symbol, style = FinanceTheme.type.bodyStrong, color = c.textPrimary, modifier = Modifier.widthIn(min = 64.dp))
                Text(match.name, style = FinanceTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TypeChip(match.type)
            }
        }
    }
}

@Composable
private fun TypeChip(type: TickerType) {
    val c = FinanceTheme.colors
    Text(
        type.label(),
        style = FinanceTheme.type.caption,
        color = c.textSecondary,
        modifier = Modifier.background(c.surfaceRaised, FinanceShapes.pill).padding(horizontal = Spacing.s, vertical = Spacing.xxs),
    )
}

@Composable
private fun PickedTicker(ticker: TickerMatch, onChange: () -> Unit) {
    val c = FinanceTheme.colors
    FinanceCard(Modifier.fillMaxWidth(), contentPadding = Spacing.m) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(CategoryIcons.of(ticker.type.icon), contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(ticker.symbol, style = FinanceTheme.type.bodyStrong, color = c.textPrimary)
                Text(
                    listOf(ticker.name, ticker.exchange, ticker.type.label()).filter { it.isNotBlank() }.joinToString(" · "),
                    style = FinanceTheme.type.caption,
                    color = c.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextAction(stringResource(R.string.asset_ticker_change), onClick = onChange)
        }
    }
}

/** "Today: 100 shares × US$ 121,79 = US$ 12.179,00", with a note when a split since the purchase rescaled the quantity. */
@Composable
private fun TodayBlock(state: AssetEditorUiState, symbol: String) {
    val c = FinanceTheme.colors
    val preview = state.preview
    val quantity = state.todayQuantity
    val value = state.todayValue
    if (!state.previewError && (preview == null || quantity == null || value == null)) return
    val formats = LocalFormats.current
    Column(
        Modifier.fillMaxWidth().background(c.surfaceRaised, FinanceShapes.medium).padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (preview == null || quantity == null || value == null) {
            Text(stringResource(R.string.asset_quote_unavailable), style = FinanceTheme.type.caption, color = c.textSecondary)
            return@Column
        }
        Text(
            stringResource(R.string.asset_today_value, unitsLabel(quantity, symbol), formats.money.formatUnitPrice(preview.price.price, Currency.USD), formats.money.format(value)),
            style = FinanceTheme.type.bodyStrong,
            color = c.textPrimary,
        )
        preview.splitsSince.firstOrNull()?.let { SplitNote(it) }
    }
}

@Composable
private fun CashFields(state: AssetEditorUiState, actions: AssetEditorActions, amount: Money?) {
    CashLinkFields(
        linked = state.fromCash,
        onLinked = { v -> actions.onEdit { it.copy(fromCash = v) } },
        inflow = true,
        assetAmount = amount,
        date = state.acquisitionDate,
        cashCurrency = state.cashCurrency ?: state.currency,
        onCashCurrency = { cur -> actions.onEdit { it.copy(cashCurrency = cur, cashDigits = null) } },
        cashDigits = state.cashDigits,
        onCashDigits = { d -> actions.onEdit { it.copy(cashDigits = d) } },
        rates = state.rates,
    )
}

@Composable
private fun NameField(state: AssetEditorUiState, actions: AssetEditorActions) {
    FinanceTextField(
        value = state.name,
        onValueChange = { v -> actions.onEdit { it.copy(name = v) } },
        label = stringResource(R.string.asset_name),
        placeholder = stringResource(R.string.asset_name_hint),
        isError = state.nameError,
        supportingText = if (state.nameError) stringResource(R.string.error_name_required) else null,
    )
}

@Composable
private fun CategoryField(state: AssetEditorUiState, actions: AssetEditorActions) {
    val c = FinanceTheme.colors
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
}

@Composable
private fun InstitutionField(state: AssetEditorUiState, actions: AssetEditorActions) =
    FinanceTextField(value = state.institution, onValueChange = { v -> actions.onEdit { it.copy(institution = v) } }, label = stringResource(R.string.asset_institution))

@Composable
private fun DescriptionField(state: AssetEditorUiState, actions: AssetEditorActions) =
    FinanceTextField(value = state.description, onValueChange = { v -> actions.onEdit { it.copy(description = v) } }, label = stringResource(R.string.asset_description))

@Composable
private fun NotesAndLogo(state: AssetEditorUiState, actions: AssetEditorActions) {
    val c = FinanceTheme.colors
    FinanceTextField(value = state.notes, onValueChange = { v -> actions.onEdit { it.copy(notes = v) } }, label = stringResource(R.string.asset_notes), singleLine = false, minLines = 2)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(stringResource(R.string.asset_logo), style = FinanceTheme.type.caption, color = c.textSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            val categoryIcon = state.categories.firstOrNull { it.id == state.categoryId }?.icon
            if (state.imageFile != null) {
                AssetIcon(state.imageFile, categoryIcon, size = LogoSize)
            } else {
                Box(Modifier.size(LogoSize).border(1.5.dp, c.outlineStrong, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(CategoryIcons.of(categoryIcon ?: "category"), contentDescription = null, tint = c.textTertiary)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), horizontalAlignment = Alignment.Start) {
                SecondaryButton(
                    stringResource(if (state.imagePath != null) R.string.asset_logo_change else R.string.asset_logo_choose),
                    onClick = actions.onPickLogo,
                    icon = Icons.Rounded.AddPhotoAlternate,
                )
                if (state.imagePath != null) TextAction(stringResource(R.string.asset_logo_remove), onClick = actions.onRemoveLogo, color = c.textSecondary)
            }
        }
        Text(stringResource(R.string.asset_logo_hint), style = FinanceTheme.type.caption, color = c.textTertiary)
    }
}

private val LogoSize = 64.dp
