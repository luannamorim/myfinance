package io.github.luannamorim.myfinance.domain.cash

import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.domain.model.EntryKind.EXPENSE
import io.github.luannamorim.myfinance.domain.model.EntryKind.INCOME
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.confirmed
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.pending
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class OccurrenceStatusTest {

    private val today = d("2026-11-12")

    @Test
    fun pending_byDueDate() {
        assertEquals(DisplayStatus.OVERDUE, OccurrenceStatus.of(pending(EXPENSE, brl("10"), "2026-11-11"), today))
        assertEquals(DisplayStatus.PENDING, OccurrenceStatus.of(pending(EXPENSE, brl("10"), "2026-11-12"), today))
        assertEquals(DisplayStatus.PENDING, OccurrenceStatus.of(pending(EXPENSE, brl("10"), "2026-11-19"), today))
        assertEquals(DisplayStatus.UPCOMING, OccurrenceStatus.of(pending(EXPENSE, brl("10"), "2026-11-20"), today))
    }

    @Test
    fun confirmedAndCancelled() {
        assertEquals(DisplayStatus.RECEIVED, OccurrenceStatus.of(confirmed(INCOME, brl("10"), "2026-11-05"), today))
        assertEquals(DisplayStatus.PAID, OccurrenceStatus.of(confirmed(EXPENSE, brl("10"), "2026-11-05"), today))
        val cancelled = pending(EXPENSE, brl("10"), "2026-11-01").copy(state = OccurrenceState.CANCELLED)
        assertEquals(DisplayStatus.CANCELLED, OccurrenceStatus.of(cancelled, today))
    }

    @Test
    fun today_usesTheLocalDate_notUtc() {
        // 02:30 UTC on 13 Nov is still 23:30 on 12 Nov in São Paulo.
        val time = FakeTimeSource(nowMs = Instant.parse("2026-11-13T02:30:00Z").toEpochMilli())
        assertEquals(d("2026-11-12"), time.today())
        val bill = pending(EXPENSE, brl("10"), "2026-11-12")
        assertEquals(DisplayStatus.PENDING, OccurrenceStatus.of(bill, time.today()))

        time.zoneId = ZoneOffset.UTC
        assertEquals(d("2026-11-13"), time.today())
        assertEquals(DisplayStatus.OVERDUE, OccurrenceStatus.of(bill, time.today()))
    }
}
