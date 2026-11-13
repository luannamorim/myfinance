package io.github.luannamorim.myfinance.domain.cash

import io.github.luannamorim.myfinance.core.format.SearchText
import io.github.luannamorim.myfinance.domain.model.CashAdjustment
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.Occurrence
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import java.time.LocalDate
import java.time.YearMonth

/** History filters. An empty set means "no filter" on that dimension. */
data class HistoryFilter(
    val query: String = "",
    val kinds: Set<EntryKind> = emptySet(),
    val statuses: Set<DisplayStatus> = emptySet(),
    val categoryIds: Set<Long> = emptySet(),
    val currencies: Set<Currency> = emptySet(),
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {
    /** Filters that only make sense for transactions (adjustments are hidden while any is set). */
    val transactionOnly: Boolean get() = query.isNotBlank() || kinds.isNotEmpty() || statuses.isNotEmpty() || categoryIds.isNotEmpty()

    val isEmpty: Boolean get() = this == HistoryFilter()
}

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
    /** Planned occurrences further ahead than this are hidden unless a date filter reaches them. */
    const val FUTURE_DAYS = 60L

    /** The date a transaction is listed under: when it happened if confirmed, when it is due otherwise. */
    fun dateOf(o: Occurrence): LocalDate = if (o.state == OccurrenceState.CONFIRMED) o.actualDate ?: o.dueDate else o.dueDate

    /**
     * Filters and sorts (newest first). [searchText] gives the searchable text of an occurrence (name, category,
     * description and note, already localized); the search ignores accents and case.
     */
    fun filter(
        occurrences: List<Occurrence>,
        adjustments: List<CashAdjustment>,
        filter: HistoryFilter,
        today: LocalDate,
        searchText: (Occurrence) -> String,
    ): List<HistoryEntry> {
        val limit = filter.to ?: today.plusDays(FUTURE_DAYS)
        fun inRange(date: LocalDate) = !date.isAfter(limit) && (filter.from == null || !date.isBefore(filter.from))
        val transactions = occurrences.asSequence()
            .filter { inRange(dateOf(it)) }
            .filter { filter.kinds.isEmpty() || it.kind in filter.kinds }
            .filter { filter.statuses.isEmpty() || OccurrenceStatus.of(it, today) in filter.statuses }
            .filter { filter.categoryIds.isEmpty() || it.categoryId in filter.categoryIds }
            .filter { filter.currencies.isEmpty() || it.expected.currency in filter.currencies || it.actual?.currency in filter.currencies }
            .filter { filter.query.isBlank() || SearchText.matches(searchText(it), filter.query) }
            .map { HistoryEntry.Transaction(it) }
            .toList()
        val adjustmentEntries = if (filter.transactionOnly) {
            emptyList()
        } else {
            adjustments
                .filter { inRange(it.date) && (filter.currencies.isEmpty() || it.amount.currency in filter.currencies) }
                .map { HistoryEntry.Adjustment(it) }
        }
        return (transactions + adjustmentEntries).sortedWith(compareByDescending<HistoryEntry> { it.date }.thenByDescending { entryId(it) })
    }

    /** Groups entries (already sorted newest first) by month. */
    fun byMonth(entries: List<HistoryEntry>): List<Pair<YearMonth, List<HistoryEntry>>> =
        entries.groupBy { YearMonth.from(it.date) }.toList()

    private fun entryId(e: HistoryEntry): Long = when (e) {
        is HistoryEntry.Transaction -> e.occurrence.id
        is HistoryEntry.Adjustment -> e.adjustment.id
    }
}
