package io.github.luannamorim.myfinance.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.core.format.AmountInput
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.Occurrence
import io.github.luannamorim.myfinance.ui.components.CurrencyInput
import io.github.luannamorim.myfinance.ui.components.DateSelector
import io.github.luannamorim.myfinance.ui.components.FinanceTextField
import io.github.luannamorim.myfinance.ui.components.PrimaryButton
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing
import java.time.LocalDate

/**
 * "Confirm payment" / "Confirm receipt" / "Confirm transfer": actual amount (prefilled with the expected one),
 * currency, date (not in the future) and an optional note. The expected amount is never changed. A transfer
 * whose cash currency differs from its asset's also asks what the asset received or gave, prefilled by
 * [suggest] (the rate of the chosen date) until the user edits it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    occurrence: Occurrence,
    today: LocalDate,
    onConfirm: (Money, LocalDate, String?, Money?) -> Unit,
    onDismiss: () -> Unit,
    target: TransferTarget? = null,
    suggest: (Money, Currency, LocalDate) -> Money? = { _, _, _ -> null },
) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    val kind = occurrence.kind
    var digits by rememberSaveable(occurrence.id) { mutableStateOf(AmountInput.digitsOf(occurrence.expected.minor)) }
    var currency by rememberSaveable(occurrence.id) { mutableStateOf(occurrence.expected.currency) }
    var date by rememberSaveable(occurrence.id) { mutableStateOf(today) }
    var note by rememberSaveable(occurrence.id) { mutableStateOf("") }
    var assetDigits by rememberSaveable(occurrence.id) { mutableStateOf<String?>(null) }
    val minor = AmountInput.minorOf(digits) ?: 0
    val crossCurrency = target != null && target.currency != currency
    val suggested = if (crossCurrency && minor > 0) suggest(Money(minor, currency), target.currency, date) else null
    val assetMinor = if (crossCurrency) AmountInput.minorOf(assetDigits ?: suggested?.let { AmountInput.digitsOf(it.minor) } ?: "") ?: 0 else 0
    val title = when (kind) {
        EntryKind.INCOME -> R.string.confirm_receipt
        EntryKind.EXPENSE -> R.string.confirm_payment
        EntryKind.TRANSFER_OUT, EntryKind.TRANSFER_IN -> R.string.confirm_transfer
    }
    val amountLabel = when (kind) {
        EntryKind.INCOME -> R.string.confirm_actual_received
        EntryKind.EXPENSE -> R.string.confirm_actual_amount
        EntryKind.TRANSFER_OUT -> R.string.confirm_transfer_out_amount
        EntryKind.TRANSFER_IN -> R.string.confirm_transfer_in_amount
    }
    val dateLabel = when (kind) {
        EntryKind.INCOME -> R.string.confirm_received_on
        EntryKind.EXPENSE -> R.string.confirm_paid_on
        EntryKind.TRANSFER_OUT, EntryKind.TRANSFER_IN -> R.string.confirm_transferred_on
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceRaised,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Column {
                Text(stringResource(title), style = FinanceTheme.type.headline, color = c.textPrimary)
                Text(occurrence.name, style = FinanceTheme.type.bodyStrong, color = c.textPrimary)
                if (kind.isTransfer && target != null) {
                    Text("${kind.directionLabel()} · ${target.name}", style = FinanceTheme.type.caption, color = c.textSecondary)
                }
                Text(
                    stringResource(R.string.confirm_expected, formats.money.format(occurrence.expected), formats.dates.date(occurrence.dueDate)),
                    style = FinanceTheme.type.caption,
                    color = c.textSecondary,
                )
            }
            CurrencyInput(
                digits = digits,
                currency = currency,
                onDigits = { digits = it },
                label = stringResource(amountLabel),
                onCurrency = { currency = it },
                isError = minor <= 0,
            )
            if (crossCurrency) {
                CurrencyInput(
                    digits = assetDigits ?: suggested?.let { AmountInput.digitsOf(it.minor) } ?: "",
                    currency = target.currency,
                    onDigits = { assetDigits = it },
                    label = stringResource(if (kind == EntryKind.TRANSFER_OUT) R.string.transfer_asset_received else R.string.transfer_asset_gave, target.currency.symbol),
                    isError = assetMinor <= 0,
                    supportingText = stringResource(R.string.transfer_asset_amount_hint),
                )
            }
            DateSelector(
                stringResource(dateLabel),
                date,
                { date = it },
                minDate = target?.acquired,
                maxDate = today,
            )
            FinanceTextField(value = note, onValueChange = { note = it }, label = stringResource(R.string.confirm_note))
            PrimaryButton(
                stringResource(R.string.action_confirm),
                onClick = { onConfirm(Money(minor, currency), date, note.ifBlank { null }, if (crossCurrency) Money(assetMinor, target.currency) else null) },
                enabled = minor > 0 && (!crossCurrency || assetMinor > 0),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
