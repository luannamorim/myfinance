package io.github.luannamorim.myfinance.ui.feature.asset

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
import androidx.compose.ui.text.input.KeyboardType
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.core.format.AmountInput
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.ui.common.CashLinkFields
import io.github.luannamorim.myfinance.ui.common.cashLinkAmount
import io.github.luannamorim.myfinance.ui.components.ChoiceRow
import io.github.luannamorim.myfinance.ui.components.CurrencyInput
import io.github.luannamorim.myfinance.ui.components.DateSelector
import io.github.luannamorim.myfinance.ui.components.FinanceTextField
import io.github.luannamorim.myfinance.ui.components.PrimaryButton
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Spacing
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = FinanceTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surfaceRaised) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(title, style = FinanceTheme.type.headline, color = c.textPrimary)
            content()
        }
    }
}

/** "Update value": a dated valuation (≤ today); warns when that date already has one. */
@Composable
fun ValuationSheet(
    currency: Currency,
    today: LocalDate,
    minDate: LocalDate?,
    existing: Set<LocalDate>,
    onSave: (LocalDate, Money, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var digits by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(today) }
    var note by rememberSaveable { mutableStateOf("") }
    val minor = AmountInput.minorOf(digits)
    Sheet(stringResource(R.string.valuation_title), onDismiss) {
        CurrencyInput(digits, currency, { digits = it }, stringResource(R.string.valuation_value))
        DateSelector(stringResource(R.string.valuation_date), date, { date = it }, maxDate = today, minDate = minDate)
        if (date in existing) Text(stringResource(R.string.valuation_replace), style = FinanceTheme.type.caption, color = FinanceTheme.colors.warning)
        FinanceTextField(value = note, onValueChange = { note = it }, label = stringResource(R.string.confirm_note))
        PrimaryButton(
            stringResource(R.string.action_save),
            onClick = { onSave(date, Money(minor ?: 0, currency), note.ifBlank { null }) },
            enabled = minor != null && digits.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Contribution, or withdrawal / partial sale / close position. A new estimated value can be recorded at the
 * same time; otherwise the flow is carried onto the last valuation.
 */
@Composable
fun FlowSheet(
    withdraw: Boolean,
    currency: Currency,
    today: LocalDate,
    minDate: LocalDate?,
    currentValue: Money?,
    onSave: (FlowKind, LocalDate, Money, BigDecimal?, String?, Money?, Money?) -> Unit,
    onDismiss: () -> Unit,
    rates: ExchangeRates = ExchangeRates.EMPTY,
) {
    val c = FinanceTheme.colors
    var kind by rememberSaveable { mutableStateOf(if (withdraw) FlowKind.WITHDRAWAL else FlowKind.CONTRIBUTION) }
    var linked by rememberSaveable { mutableStateOf(false) }
    var cashCurrency by rememberSaveable { mutableStateOf(currency) }
    var cashDigits by rememberSaveable { mutableStateOf<String?>(null) }
    var digits by rememberSaveable { mutableStateOf("") }
    var valueDigits by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(today) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val minor = AmountInput.minorOf(digits) ?: 0
    val quantityResult = QuantityInput.parse(quantity)
    Sheet(stringResource(if (withdraw) R.string.flow_title_withdraw else R.string.flow_title_contribute), onDismiss) {
        if (withdraw) {
            ChoiceRow(
                "",
                listOf(
                    FlowKind.WITHDRAWAL to stringResource(R.string.flow_withdrawal),
                    FlowKind.PARTIAL_DISPOSAL to stringResource(R.string.flow_partial),
                    FlowKind.FULL_DISPOSAL to stringResource(R.string.flow_full),
                ),
                kind,
                { kind = it },
            )
        }
        CurrencyInput(digits, currency, { digits = it }, stringResource(R.string.flow_amount))
        if (kind == FlowKind.FULL_DISPOSAL) {
            Text(stringResource(R.string.flow_full_hint), style = FinanceTheme.type.caption, color = c.textSecondary)
        } else if (withdraw && currentValue != null && minor > currentValue.minor) {
            Text(stringResource(R.string.flow_more_than_value), style = FinanceTheme.type.caption, color = c.warning)
        }
        DateSelector(stringResource(R.string.flow_date), date, { date = it }, maxDate = today, minDate = minDate)
        FinanceTextField(
            value = quantity,
            onValueChange = { quantity = it },
            label = stringResource(R.string.flow_quantity),
            keyboardType = KeyboardType.Decimal,
            isError = quantityResult.isFailure,
        )
        if (kind != FlowKind.FULL_DISPOSAL) {
            CurrencyInput(valueDigits, currency, { valueDigits = it }, stringResource(R.string.flow_new_value), supportingText = stringResource(R.string.flow_new_value_hint))
        }
        CashLinkFields(
            linked = linked,
            onLinked = { linked = it },
            inflow = !withdraw,
            assetAmount = Money(minor, currency).takeIf { minor > 0 },
            date = date,
            cashCurrency = cashCurrency,
            onCashCurrency = { cashCurrency = it; cashDigits = null },
            cashDigits = cashDigits,
            onCashDigits = { cashDigits = it },
            rates = rates,
        )
        FinanceTextField(value = note, onValueChange = { note = it }, label = stringResource(R.string.confirm_note))
        val cash = if (minor > 0) cashLinkAmount(linked, Money(minor, currency), date, cashCurrency, cashDigits, rates) else null
        PrimaryButton(
            stringResource(R.string.action_save),
            onClick = {
                val newValue = if (kind != FlowKind.FULL_DISPOSAL && valueDigits.isNotEmpty()) AmountInput.minorOf(valueDigits)?.let { Money(it, currency) } else null
                onSave(kind, date, Money(minor, currency), quantityResult.getOrNull(), note.ifBlank { null }, newValue, cash)
            },
            enabled = minor > 0 && quantityResult.isSuccess && (!linked || cash != null),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
