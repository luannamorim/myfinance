package io.github.luannamorim.myfinance.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.domain.cash.DisplayStatus
import io.github.luannamorim.myfinance.domain.cash.RecurrenceRule
import io.github.luannamorim.myfinance.domain.model.RecurrenceUnit

/** Presets in the order the editor offers them (most common first). */
val PresetOrder = listOf(
    RecurrenceRule.Preset.MONTHLY,
    RecurrenceRule.Preset.WEEKLY,
    RecurrenceRule.Preset.BIWEEKLY,
    RecurrenceRule.Preset.BIMONTHLY,
    RecurrenceRule.Preset.QUARTERLY,
    RecurrenceRule.Preset.SEMIANNUAL,
    RecurrenceRule.Preset.ANNUAL,
    RecurrenceRule.Preset.DAILY,
)

@Composable
fun RecurrenceRule.Preset.label(): String = stringResource(
    when (this) {
        RecurrenceRule.Preset.DAILY -> R.string.repeat_daily
        RecurrenceRule.Preset.WEEKLY -> R.string.repeat_weekly
        RecurrenceRule.Preset.BIWEEKLY -> R.string.repeat_biweekly
        RecurrenceRule.Preset.MONTHLY -> R.string.repeat_monthly
        RecurrenceRule.Preset.BIMONTHLY -> R.string.repeat_bimonthly
        RecurrenceRule.Preset.QUARTERLY -> R.string.repeat_quarterly
        RecurrenceRule.Preset.SEMIANNUAL -> R.string.repeat_semiannual
        RecurrenceRule.Preset.ANNUAL -> R.string.repeat_annual
    },
)

/** "Monthly", or "Every 3 weeks" for a custom rule. */
@Composable
fun RecurrenceRule.label(): String = presetOrNull()?.label() ?: when (unit) {
    RecurrenceUnit.DAY -> pluralStringResource(R.plurals.repeat_every_days, interval, interval)
    RecurrenceUnit.WEEK -> pluralStringResource(R.plurals.repeat_every_weeks, interval, interval)
    RecurrenceUnit.MONTH -> pluralStringResource(R.plurals.repeat_every_months, interval, interval)
    RecurrenceUnit.YEAR -> pluralStringResource(R.plurals.repeat_every_years, interval, interval)
}

@Composable
fun RecurrenceUnit.label(): String = stringResource(
    when (this) {
        RecurrenceUnit.DAY -> R.string.unit_days
        RecurrenceUnit.WEEK -> R.string.unit_weeks
        RecurrenceUnit.MONTH -> R.string.unit_months
        RecurrenceUnit.YEAR -> R.string.unit_years
    },
)

@Composable
fun DisplayStatus.label(): String = stringResource(
    when (this) {
        DisplayStatus.UPCOMING -> R.string.status_upcoming
        DisplayStatus.PENDING -> R.string.status_pending
        DisplayStatus.OVERDUE -> R.string.status_overdue
        DisplayStatus.PAID -> R.string.status_paid
        DisplayStatus.RECEIVED -> R.string.status_received
        DisplayStatus.CANCELLED -> R.string.status_cancelled
    },
)
