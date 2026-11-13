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
 * "Confirm payment" / "Confirm receipt": actual amount (prefilled with the expected one), currency, date (not in
 * the future) and an optional note. The expected amount is never changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    occurrence: Occurrence,
    today: LocalDate,
    onConfirm: (Money, LocalDate, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    val income = occurrence.kind == EntryKind.INCOME
    var digits by rememberSaveable(occurrence.id) { mutableStateOf(AmountInput.digitsOf(occurrence.expected.minor)) }
    var currency by rememberSaveable(occurrence.id) { mutableStateOf(occurrence.expected.currency) }
    var date by rememberSaveable(occurrence.id) { mutableStateOf(today) }
    var note by rememberSaveable(occurrence.id) { mutableStateOf("") }
    val minor = AmountInput.minorOf(digits) ?: 0
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
                Text(stringResource(if (income) R.string.confirm_receipt else R.string.confirm_payment), style = FinanceTheme.type.headline, color = c.textPrimary)
                Text(occurrence.name, style = FinanceTheme.type.bodyStrong, color = c.textPrimary)
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
                label = stringResource(if (income) R.string.confirm_actual_received else R.string.confirm_actual_amount),
                onCurrency = { currency = it },
                isError = minor <= 0,
            )
            DateSelector(
                stringResource(if (income) R.string.confirm_received_on else R.string.confirm_paid_on),
                date,
                { date = it },
                maxDate = today,
            )
            FinanceTextField(value = note, onValueChange = { note = it }, label = stringResource(R.string.confirm_note))
            PrimaryButton(
                stringResource(R.string.action_confirm),
                onClick = { onConfirm(Money(minor, currency), date, note.ifBlank { null }) },
                enabled = minor > 0,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
