package io.github.luannamorim.myfinance.domain.cash

import io.github.luannamorim.myfinance.core.format.SearchText
import io.github.luannamorim.myfinance.domain.model.AdjustmentKind
import io.github.luannamorim.myfinance.domain.model.CashAdjustment
import io.github.luannamorim.myfinance.domain.model.Occurrence
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import java.time.LocalDate
import java.time.YearMonth

/** What History narrows a month to: what is still open (overdue, pending, upcoming) or what is done. */
enum class HistoryProgress { OPEN, DONE }

/** History shows one [month], optionally narrowed by a search [query] and by [progress] (null: the whole month). */
data class HistoryFilter(
    val month: YearMonth,
    val query: String = "",
    val progress: HistoryProgress? = null,
)

/** One history line: a transaction or a balance adjustment. */
sealed interface HistoryEntry {
    val date: LocalDate

    data class Transaction(val occurrence: Occurrence) : HistoryEntry {
        override val date: LocalDate get() = History.dateOf(occurrence)
    }

    data class Adjustment(val adjustment: CashAdjustment) : HistoryEntry {
        override val date: LocalDate get() = adjustment.date
    }
}

object History {
    /** The date a transaction is listed under: when it happened if confirmed, when it is due otherwise. */
    fun dateOf(o: Occurrence): LocalDate = if (o.state == OccurrenceState.CONFIRMED) o.actualDate ?: o.dueDate else o.dueDate

    /**
     * The month's entries, newest first. [searchText] gives the searchable text of an occurrence (name, category,
     * description and note, already localized); the search ignores accents and case. Cancelled occurrences show only
     * without [HistoryFilter.progress]; balance adjustments count as done and are left out of a search. Opening
     * balances are the starting point (Settings → Opening balances), not history lines.
     */
    fun filter(
        occurrences: List<Occurrence>,
        adjustments: List<CashAdjustment>,
        filter: HistoryFilter,
        searchText: (Occurrence) -> String,
    ): List<HistoryEntry> {
        val transactions = occurrences.asSequence()
            .filter { YearMonth.from(dateOf(it)) == filter.month }
            .filter { it.state.matches(filter.progress) }
            .filter { filter.query.isBlank() || SearchText.matches(searchText(it), filter.query) }
            .map { HistoryEntry.Transaction(it) }
            .toList()
        val adjustmentEntries = if (filter.query.isNotBlank() || filter.progress == HistoryProgress.OPEN) {
            emptyList()
        } else {
            adjustments.filter { it.kind == AdjustmentKind.ADJUSTMENT && YearMonth.from(it.date) == filter.month }.map { HistoryEntry.Adjustment(it) }
        }
        return (transactions + adjustmentEntries).sortedWith(compareByDescending<HistoryEntry> { it.date }.thenByDescending { entryId(it) })
    }

    private fun OccurrenceState.matches(progress: HistoryProgress?): Boolean = when (progress) {
        null -> true
        HistoryProgress.OPEN -> this == OccurrenceState.PENDING
        HistoryProgress.DONE -> this == OccurrenceState.CONFIRMED
    }

    private fun entryId(e: HistoryEntry): Long = when (e) {
        is HistoryEntry.Transaction -> e.occurrence.id
        is HistoryEntry.Adjustment -> e.adjustment.id
    }
}
