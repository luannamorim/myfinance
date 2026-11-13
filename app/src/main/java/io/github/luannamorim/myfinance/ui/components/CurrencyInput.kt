package io.github.luannamorim.myfinance.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.luannamorim.myfinance.core.format.AmountInput
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.theme.FinanceShapes
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme

/**
 * Cash-register amount field: digits fill in from the cents, so "31240" reads 312,40 on any keyboard.
 * [digits] is the only state (see AmountInput). With [onCurrency], a R$/US$ choice sits under the field.
 */
@Composable
fun CurrencyInput(
    digits: String,
    currency: Currency,
    onDigits: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onCurrency: ((Currency) -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    val c = FinanceTheme.colors
    val formats = LocalFormats.current
    val shown = formats.money.formatNumber(AmountInput.minorOf(digits) ?: 0, currency)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = TextFieldValue(shown, selection = TextRange(shown.length)),
            onValueChange = { onDigits(AmountInput.sanitize(it.text)) },
            label = { Text(label) },
            prefix = { Text(currency.symbol + " ", style = FinanceTheme.type.numeric, color = c.textSecondary) },
            textStyle = FinanceTheme.type.numericLarge.copy(color = c.textPrimary),
            singleLine = true,
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = imeAction),
            shape = FinanceShapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.textPrimary,
                unfocusedBorderColor = c.outline,
                focusedLabelColor = c.textPrimary,
                unfocusedLabelColor = c.textSecondary,
                cursorColor = c.accent,
                focusedContainerColor = c.surface,
                unfocusedContainerColor = c.surface,
                errorBorderColor = c.danger,
                errorLabelColor = c.danger,
                errorSupportingTextColor = c.danger,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (onCurrency != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Currency.entries.forEach { option ->
                    FinanceChip(option.symbol, selected = option == currency, onClick = { onCurrency(option) })
                }
            }
        }
    }
}
