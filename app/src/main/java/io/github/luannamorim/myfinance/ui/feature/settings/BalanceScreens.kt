package io.github.luannamorim.myfinance.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.components.AmountDisplay
import io.github.luannamorim.myfinance.ui.components.AmountTone
import io.github.luannamorim.myfinance.ui.components.ConfirmDialog
import io.github.luannamorim.myfinance.ui.components.CurrencyInput
import io.github.luannamorim.myfinance.ui.components.DateSelector
import io.github.luannamorim.myfinance.ui.components.DetailScaffold
import io.github.luannamorim.myfinance.ui.components.FinanceCard
import io.github.luannamorim.myfinance.ui.components.FinanceChip
import io.github.luannamorim.myfinance.ui.components.FinanceTextField
import io.github.luannamorim.myfinance.ui.components.PrimaryButton
import io.github.luannamorim.myfinance.ui.components.SectionHeader
import io.github.luannamorim.myfinance.ui.components.SecondaryButton
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing

@Composable
fun OpeningBalancesScreen(onBack: () -> Unit, viewModel: BalanceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OpeningBalancesContent(state, onBack, viewModel::editOpening, viewModel::saveOpening)
}

@Composable
fun OpeningBalancesContent(
    state: BalanceUiState,
    onBack: () -> Unit,
    onEdit: (Currency, (OpeningForm) -> OpeningForm) -> Unit,
    onSave: (Currency) -> Unit,
) {
    val c = FinanceTheme.colors
    DetailScaffold(title = stringResource(R.string.opening_title), onBack = onBack) {
        Text(stringResource(R.string.opening_intro), style = FinanceTheme.type.body, color = c.textSecondary)
        Currency.entries.forEach { currency ->
            val form = state.openingForms[currency] ?: return@forEach
            SectionHeader(stringResource(R.string.opening_currency, currency.symbol), Modifier.padding(top = Spacing.s))
            FinanceCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    CurrencyInput(
                        digits = form.digits,
                        currency = currency,
                        onDigits = { d -> onEdit(currency) { it.copy(digits = d) } },
                        label = stringResource(R.string.opening_amount),
                    )
                    FinanceChip(stringResource(R.string.opening_negative), selected = form.negative, onClick = { onEdit(currency) { it.copy(negative = !it.negative) } })
                    DateSelector(stringResource(R.string.opening_date), form.date, { d -> onEdit(currency) { it.copy(date = d) } }, maxDate = state.today)
                    if (form.error) Text(stringResource(R.string.opening_after_adjustment), style = FinanceTheme.type.caption, color = c.danger)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        SecondaryButton(stringResource(R.string.action_save), onClick = { onSave(currency) })
                        val current = state.openings[currency]
                        Text(
                            if (form.saved) stringResource(R.string.opening_saved)
                            else current?.let { LocalFormats.current.money.format(it.amount) + " · " + LocalFormats.current.dates.date(it.date) }
                                ?: stringResource(R.string.opening_not_set),
                            style = FinanceTheme.type.caption,
                            color = if (form.saved) c.positive else c.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

data class AdjustActions(
    val onBack: () -> Unit = {},
    val onEdit: ((AdjustForm) -> AdjustForm) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: (Long) -> Unit = {},
)

@Composable
fun AdjustBalanceScreen(onBack: () -> Unit, viewModel: BalanceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AdjustBalanceContent(state, AdjustActions(onBack, viewModel::editAdjust, viewModel::saveAdjustment, { viewModel.deleteAdjustment(it) }))
}

@Composable
fun AdjustBalanceContent(state: BalanceUiState, actions: AdjustActions) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    val form = state.adjust
    val opening = state.openings[form.currency]
    DetailScaffold(
        title = stringResource(R.string.adjust_title),
        onBack = actions.onBack,
        bottomBar = {
            PrimaryButton(stringResource(R.string.adjust_save), onClick = actions.onSave, enabled = state.adjustTarget != null && opening != null, modifier = Modifier.fillMaxWidth())
        },
    ) {
        Text(stringResource(R.string.adjust_intro), style = FinanceTheme.type.body, color = c.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Currency.entries.forEach { cur -> FinanceChip(cur.symbol, selected = cur == form.currency, onClick = { actions.onEdit { it.copy(currency = cur, digits = "") } }) }
        }
        if (opening == null) {
            Text(stringResource(R.string.adjust_needs_opening), style = FinanceTheme.type.caption, color = c.warning)
        }
        FinanceCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.adjust_current), style = FinanceTheme.type.caption, color = c.textSecondary)
            state.balances[form.currency]?.let { AmountDisplay(it, style = FinanceTheme.type.numericLarge) }
        }
        CurrencyInput(digits = form.digits, currency = form.currency, onDigits = { d -> actions.onEdit { it.copy(digits = d) } }, label = stringResource(R.string.adjust_real))
        FinanceChip(stringResource(R.string.opening_negative), selected = form.negative, onClick = { actions.onEdit { it.copy(negative = !it.negative) } })
        DateSelector(stringResource(R.string.adjust_date), form.date, { d -> actions.onEdit { it.copy(date = d) } }, maxDate = state.today, minDate = opening?.date)
        FinanceTextField(value = form.note, onValueChange = { n -> actions.onEdit { it.copy(note = n) } }, label = stringResource(R.string.adjust_note))
        state.adjustDifference?.let { diff ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.adjust_difference) + " ", style = FinanceTheme.type.caption, color = c.textSecondary)
                AmountDisplay(diff, signed = true, tone = AmountTone.AUTO)
            }
        }
        when (state.message) {
            BalanceMessage.NO_DIFFERENCE -> Text(stringResource(R.string.adjust_none), style = FinanceTheme.type.caption, color = c.textSecondary)
            BalanceMessage.NEEDS_OPENING -> Text(stringResource(R.string.adjust_needs_opening), style = FinanceTheme.type.caption, color = c.warning)
            else -> Unit
        }
        if (state.adjustments.isNotEmpty()) {
            SectionHeader(stringResource(R.string.adjust_history), Modifier.padding(top = Spacing.s))
            state.adjustments.forEach { a ->
                FinanceCard(Modifier.fillMaxWidth(), contentPadding = Spacing.m) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(formats.dates.date(a.date), style = FinanceTheme.type.bodyStrong, color = c.textPrimary)
                            a.note?.let { Text(it, style = FinanceTheme.type.caption, color = c.textSecondary) }
                        }
                        AmountDisplay(a.amount, signed = true)
                        IconButton(onClick = { deleting = a.id }) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.action_delete), tint = c.textSecondary) }
                    }
                }
            }
        }
    }
    deleting?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.adjust_delete_title),
            message = stringResource(R.string.adjust_delete_message),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { deleting = null; actions.onDelete(id) },
            onDismiss = { deleting = null },
            destructive = true,
        )
    }
}
