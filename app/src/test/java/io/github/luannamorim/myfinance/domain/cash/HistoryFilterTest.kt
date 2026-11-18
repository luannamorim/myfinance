package io.github.luannamorim.myfinance.domain.cash

import io.github.luannamorim.myfinance.domain.model.EntryKind.EXPENSE
import io.github.luannamorim.myfinance.domain.model.EntryKind.INCOME
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.testutil.adjustment
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.confirmed
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.opening
import io.github.luannamorim.myfinance.testutil.pending
import io.github.luannamorim.myfinance.testutil.usd
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class HistoryFilterTest {

    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)
    private val electricity = pending(EXPENSE, brl("250.00"), "2026-11-20", category = 40, name = "Energia elétrica")
    private val salary = confirmed(INCOME, brl("9000.00"), "2026-11-05", on = "2026-11-06", category = 10, name = "Salary")
    private val rentOct = confirmed(EXPENSE, brl("2500.00"), "2026-10-10", category = 20, name = "Rent")
    private val overdue = pending(EXPENSE, brl("120.00"), "2026-10-25", category = 50, name = "Internet")
    private val cancelled = pending(EXPENSE, brl("99.00"), "2026-11-15", category = 50, name = "Gym").copy(state = OccurrenceState.CANCELLED)
    private val farFuture = pending(EXPENSE, brl("2500.00"), "2027-05-10", category = 20, name = "Rent")
    private val cloud = pending(EXPENSE, usd("20.00"), "2026-11-28", category = 40, name = "Cloud")
    private val paidLate = confirmed(EXPENSE, brl("80.00"), "2026-10-31", on = "2026-11-01", category = 50, name = "Water")
    private val all = listOf(electricity, salary, rentOct, overdue, cancelled, farFuture, cloud, paidLate)
    private val adj = adjustment(brl("-37.60"), "2026-11-11")

    private fun run(filter: HistoryFilter) =
        History.filter(all, listOf(adj), filter) { it.name }.map {
            when (it) {
                is HistoryEntry.Transaction -> it.occurrence.name
                is HistoryEntry.Adjustment -> "adjustment"
            }
        }

    @Test
    fun wholeMonth_newestFirst_withAdjustmentsAndCancelled() {
        assertEquals(listOf("Cloud", "Energia elétrica", "Gym", "adjustment", "Salary", "Water"), run(HistoryFilter(nov)))
        assertEquals(listOf("Internet", "Rent"), run(HistoryFilter(oct)))
        // A month far ahead still shows what is planned there.
        assertEquals(listOf("Rent"), run(HistoryFilter(YearMonth.of(2027, 5))))
        assertEquals(emptyList<String>(), run(HistoryFilter(YearMonth.of(2026, 9))))
    }

    @Test
    fun open_isWhatIsStillToHappen_overdueIncluded() {
        assertEquals(listOf("Cloud", "Energia elétrica"), run(HistoryFilter(nov, progress = HistoryProgress.OPEN)))
        assertEquals(listOf("Internet"), run(HistoryFilter(oct, progress = HistoryProgress.OPEN)))
    }

    @Test
    fun done_isWhatWasConfirmed_andTheAdjustments() {
        assertEquals(listOf("adjustment", "Salary", "Water"), run(HistoryFilter(nov, progress = HistoryProgress.DONE)))
        assertEquals(listOf("Rent"), run(HistoryFilter(oct, progress = HistoryProgress.DONE)))
    }

    @Test
    fun search_staysInTheMonth_ignoresAccentsAndCase_andHidesAdjustments() {
        assertEquals(listOf("Energia elétrica"), run(HistoryFilter(nov, query = "ELETRICA")))
        assertEquals(listOf("Rent"), run(HistoryFilter(oct, query = "rent")))
        assertEquals(emptyList<String>(), run(HistoryFilter(nov, query = "rent")))
        // With a chip too: both apply.
        assertEquals(emptyList<String>(), run(HistoryFilter(nov, query = "eletrica", progress = HistoryProgress.DONE)))
    }

    @Test
    fun openingBalances_areNotListed() {
        // A US$ 0,00 opening saved today: before 1.4.1 it showed as "Balance adjustment · R$ 0,00".
        val zeroOpening = opening(usd("0.00"), "2026-11-12")
        val listed = { f: HistoryFilter -> History.filter(emptyList(), listOf(zeroOpening, adj), f) { it.name }.map { (it as HistoryEntry.Adjustment).adjustment } }
        assertEquals(listOf(adj), listed(HistoryFilter(nov)))
        assertEquals(listOf(adj), listed(HistoryFilter(nov, progress = HistoryProgress.DONE)))
    }

    @Test
    fun confirmedIsListedOnItsActualDate() {
        // Due 31/10, paid 01/11: it is November's.
        assertEquals(d("2026-11-01"), History.dateOf(paidLate))
        assertEquals(d("2026-11-06"), History.dateOf(salary))
        assertEquals(d("2026-11-20"), History.dateOf(electricity))
    }
}
