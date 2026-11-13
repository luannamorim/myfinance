package io.github.luannamorim.myfinance.domain.cash

import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.Occurrence
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import java.time.LocalDate

/** What the user sees for an occurrence. */
enum class DisplayStatus { UPCOMING, PENDING, OVERDUE, PAID, RECEIVED, CANCELLED }

object OccurrenceStatus {
    /** A pending occurrence due within this many days (today included) is "Pending"; later ones are "Upcoming". */
    const val PENDING_WINDOW_DAYS = 7L

    fun of(occurrence: Occurrence, today: LocalDate): DisplayStatus = when (occurrence.state) {
        OccurrenceState.CANCELLED -> DisplayStatus.CANCELLED
        OccurrenceState.CONFIRMED -> if (occurrence.kind == EntryKind.INCOME) DisplayStatus.RECEIVED else DisplayStatus.PAID
        OccurrenceState.PENDING -> when {
            occurrence.dueDate.isBefore(today) -> DisplayStatus.OVERDUE
            !occurrence.dueDate.isAfter(today.plusDays(PENDING_WINDOW_DAYS)) -> DisplayStatus.PENDING
            else -> DisplayStatus.UPCOMING
        }
    }
}
