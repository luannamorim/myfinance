package io.github.luannamorim.myfinance.domain.cash

import io.github.luannamorim.myfinance.domain.model.RecurrenceUnit
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Recurrence dates. The n-th date is always computed from the anchor ("anchor + n × interval"), never from
 * the previous date, so month ends don't drift: 31 Jan → 28/29 Feb → 31 Mar. java.time clamps to the last day
 * of shorter months and maps 29 Feb to 28 Feb in common years.
 */
object Recurrence {

    /** Occurrences are materialized through the end of the month this many months ahead of today. */
    const val HORIZON_MONTHS = 12L

    fun dateAt(anchor: LocalDate, rule: RecurrenceRule, n: Int): LocalDate = dateAt(anchor, rule, n.toLong())

    private fun dateAt(anchor: LocalDate, rule: RecurrenceRule, n: Long): LocalDate {
        val steps = n * rule.interval
        return when (rule.unit) {
            RecurrenceUnit.DAY -> anchor.plusDays(steps)
            RecurrenceUnit.WEEK -> anchor.plusWeeks(steps)
            RecurrenceUnit.MONTH -> anchor.plusMonths(steps)
            RecurrenceUnit.YEAR -> anchor.plusYears(steps)
        }
    }

    /** Index of the first date on or after [date] (0 when [date] is on or before the anchor). */
    private fun firstIndexOnOrAfter(anchor: LocalDate, rule: RecurrenceRule, date: LocalDate): Long {
        if (!date.isAfter(anchor)) return 0
        val unit = when (rule.unit) {
            RecurrenceUnit.DAY -> ChronoUnit.DAYS
            RecurrenceUnit.WEEK -> ChronoUnit.WEEKS
            RecurrenceUnit.MONTH -> ChronoUnit.MONTHS
            RecurrenceUnit.YEAR -> ChronoUnit.YEARS
        }
        // Start just below the estimate (clamping can only make a date earlier) and walk forward.
        var n = (unit.between(anchor, date) / rule.interval - 1).coerceAtLeast(0)
        while (dateAt(anchor, rule, n).isBefore(date)) n++
        return n
    }

    fun firstOnOrAfter(anchor: LocalDate, rule: RecurrenceRule, date: LocalDate): LocalDate =
        dateAt(anchor, rule, firstIndexOnOrAfter(anchor, rule, date))

    /** Nominal dates in [from]..[to] (inclusive), never after [endDate]. */
    fun datesBetween(anchor: LocalDate, rule: RecurrenceRule, from: LocalDate, to: LocalDate, endDate: LocalDate?): List<LocalDate> {
        val last = if (endDate != null && endDate.isBefore(to)) endDate else to
        if (from.isAfter(last)) return emptyList()
        val result = mutableListOf<LocalDate>()
        var n = firstIndexOnOrAfter(anchor, rule, from)
        while (true) {
            val date = dateAt(anchor, rule, n)
            if (date.isAfter(last)) break
            result += date
            n++
        }
        return result
    }

    /** Last day of the month [HORIZON_MONTHS] months after [today]'s month. */
    fun horizonEnd(today: LocalDate): LocalDate = YearMonth.from(today).plusMonths(HORIZON_MONTHS).atEndOfMonth()
}
