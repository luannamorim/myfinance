package io.github.luannamorim.myfinance.domain.cash

import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind.EXPENSE
import io.github.luannamorim.myfinance.domain.model.EntryKind.INCOME
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.testutil.adjustment
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.confirmed
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.pending
import io.github.luannamorim.myfinance.testutil.usd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class HistoryFilterTest {

    private val today = d("2026-11-12")
    private val electricity = pending(EXPENSE, brl("250.00"), "2026-11-20", category = 40, name = "Energia elétrica")
    private val salary = confirmed(INCOME, brl("9000.00"), "2026-11-05", on = "2026-11-06", category = 10, name = "Salary")
    private val rentOct = confirmed(EXPENSE, brl("2500.00"), "2026-10-10", category = 20, name = "Rent")
    private val overdue = pending(EXPENSE, brl("120.00"), "2026-10-25", category = 50, name = "Internet")
    private val cancelled = pending(EXPENSE, brl("99.00"), "2026-11-15", category = 50, name = "Gym").copy(state = OccurrenceState.CANCELLED)
    private val farFuture = pending(EXPENSE, brl("2500.00"), "2027-05-10", category = 20, name = "Rent")
    private val cloud = pending(EXPENSE, usd("20.00"), "2026-11-28", category = 40, name = "Cloud")
    private val all = listOf(electricity, salary, rentOct, overdue, cancelled, farFuture, cloud)
    private val adj = adjustment(brl("-37.60"), "2026-11-11")

    private fun run(filter: HistoryFilter) =
        History.filter(all, listOf(adj), filter, today) { it.name }.map {
            when (it) {
                is HistoryEntry.Transaction -> it.occurrence.name
                is HistoryEntry.Adjustment -> "adjustment"
            }
        }

    @Test
    fun noFilter_newestFirst_hidesFarFuture_andIncludesAdjustments() {
        assertEquals(
            listOf("Cloud", "Energia elétrica", "Gym", "adjustment", "Salary", "Internet", "Rent"),
            run(HistoryFilter()),
        )
    }

    @Test
    fun search_ignoresAccentsAndCase_andHidesAdjustments() {
        assertEquals(listOf("Energia elétrica"), run(HistoryFilter(query = "ELETRICA")))
    }

    @Test
    fun eachFilter() {
        assertEquals(listOf("Salary"), run(HistoryFilter(kinds = setOf(INCOME))))
        assertEquals(listOf("Internet"), run(HistoryFilter(statuses = setOf(DisplayStatus.OVERDUE))))
        assertEquals(listOf("Gym"), run(HistoryFilter(statuses = setOf(DisplayStatus.CANCELLED))))
        assertEquals(listOf("Cloud", "Energia elétrica"), run(HistoryFilter(categoryIds = setOf(40))))
        assertEquals(listOf("Cloud"), run(HistoryFilter(currencies = setOf(Currency.USD))))
    }

    @Test
    fun combinedFilters_andDateRange() {
        assertEquals(listOf("Rent"), run(HistoryFilter(kinds = setOf(EXPENSE), statuses = setOf(DisplayStatus.PAID))))
        assertEquals(listOf("Internet", "Rent"), run(HistoryFilter(to = d("2026-10-31"))))
        // A date filter reaching further than 60 days shows planned occurrences there.
        assertTrue("Rent" in run(HistoryFilter(from = d("2027-05-01"), to = d("2027-05-31"))))
    }

    @Test
    fun confirmedIsListedOnItsActualDate_andGroupedByMonth() {
        val groups = History.byMonth(History.filter(all, emptyList(), HistoryFilter(), today) { it.name })
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), groups.map { it.first })
        assertEquals(d("2026-11-06"), History.dateOf(salary))
    }
}
