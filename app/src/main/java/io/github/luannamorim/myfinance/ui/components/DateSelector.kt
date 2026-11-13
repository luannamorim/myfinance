package io.github.luannamorim.myfinance.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Full-width "Label: date" button that opens a date picker. Dates outside [minDate]..[maxDate] can't be picked. */
@Composable
fun DateSelector(
    label: String,
    date: LocalDate,
    onDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    maxDate: LocalDate? = null,
    minDate: LocalDate? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SecondaryButton("$label: ${LocalFormats.current.dates.date(date)}", onClick = { open = true }, icon = Icons.Rounded.CalendarMonth, modifier = modifier)
    if (open) FinanceDatePickerDialog(date, onDate = { onDate(it); open = false }, onDismiss = { open = false }, maxDate = maxDate, minDate = minDate)
}

/** Material date picker in the app's style. The picker works in UTC millis; conversion stays here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceDatePickerDialog(
    date: LocalDate,
    onDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    maxDate: LocalDate? = null,
    minDate: LocalDate? = null,
) {
    val c = FinanceTheme.colors
    val selectable = remember(maxDate, minDate) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val day = utcTimeMillis.toLocalDate()
                return (maxDate == null || !day.isAfter(maxDate)) && (minDate == null || !day.isBefore(minDate))
            }
            override fun isSelectableYear(year: Int) = (maxDate == null || year <= maxDate.year) && (minDate == null || year >= minDate.year)
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = selectable,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextAction(stringResource(R.string.action_save), onClick = {
                state.selectedDateMillis?.let { onDate(it.toLocalDate()) } ?: onDismiss()
            })
        },
        dismissButton = { TextAction(stringResource(R.string.action_cancel), onClick = onDismiss, color = c.textSecondary) },
    ) { DatePicker(state = state) }
}

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
