package io.github.luannamorim.myfinance.domain.cash

import io.github.luannamorim.myfinance.domain.model.RecurrenceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {

    private fun d(text: String) = LocalDate.parse(text)
    private fun dates(vararg texts: String) = texts.map(::d)

    private fun first(anchor: String, rule: RecurrenceRule, count: Int) =
        (0 until count).map { Recurrence.dateAt(d(anchor), rule, it) }

    @Test
    fun monthly_fromMonthEnd_clampsWithoutDrift() {
        assertEquals(
            dates("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31"),
            first("2026-01-31", RecurrenceRule.MONTHLY, 5),
        )
        assertEquals(d("2028-02-29"), Recurrence.dateAt(d("2028-01-31"), RecurrenceRule.MONTHLY, 1))
    }

    @Test
    fun annual_fromLeapDay() {
        assertEquals(
            dates("2024-02-29", "2025-02-28", "2026-02-28", "2027-02-28", "2028-02-29"),
            first("2024-02-29", RecurrenceRule.ANNUAL, 5),
        )
    }

    @Test
    fun weekly_and_biweekly() {
        // 2026-11-12 is a Thursday.
        assertEquals(dates("2026-11-12", "2026-11-19", "2026-11-26"), first("2026-11-12", RecurrenceRule.WEEKLY, 3))
        assertEquals(dates("2026-11-12", "2026-11-26", "2026-12-10"), first("2026-11-12", RecurrenceRule.BIWEEKLY, 3))
    }

    @Test
    fun quarterly_semiannual_bimonthly() {
        assertEquals(dates("2026-11-30", "2027-02-28", "2027-05-30"), first("2026-11-30", RecurrenceRule.QUARTERLY, 3))
        assertEquals(dates("2026-08-31", "2027-02-28", "2027-08-31"), first("2026-08-31", RecurrenceRule.SEMIANNUAL, 3))
        assertEquals(dates("2026-12-31", "2027-02-28", "2027-04-30"), first("2026-12-31", RecurrenceRule.BIMONTHLY, 3))
    }

    @Test
    fun daily_crossesMonthEnd() {
        assertEquals(dates("2026-11-29", "2026-11-30", "2026-12-01"), first("2026-11-29", RecurrenceRule.DAILY, 3))
    }

    @Test
    fun custom_everyThreeWeeks() {
        val rule = RecurrenceRule(RecurrenceUnit.WEEK, 3)
        assertNull(rule.presetOrNull())
        assertEquals(dates("2026-11-12", "2026-12-03", "2026-12-24"), first("2026-11-12", rule, 3))
    }

    @Test
    fun datesBetween_respectsWindowAndEndDate() {
        val anchor = d("2026-01-31")
        assertEquals(
            dates("2026-02-28", "2026-03-31", "2026-04-30"),
            Recurrence.datesBetween(anchor, RecurrenceRule.MONTHLY, d("2026-02-01"), d("2026-04-30"), endDate = null),
        )
        assertEquals(
            dates("2026-02-28", "2026-03-31"),
            Recurrence.datesBetween(anchor, RecurrenceRule.MONTHLY, d("2026-02-01"), d("2026-12-31"), endDate = d("2026-04-29")),
        )
        // Window starting on an occurrence includes it; window before the anchor starts at the anchor.
        assertEquals(
            dates("2026-01-31", "2026-02-28"),
            Recurrence.datesBetween(anchor, RecurrenceRule.MONTHLY, d("2025-06-01"), d("2026-03-01"), endDate = null),
        )
        assertTrue(Recurrence.datesBetween(anchor, RecurrenceRule.MONTHLY, d("2026-05-01"), d("2026-12-31"), endDate = d("2026-04-30")).isEmpty())
        assertTrue(Recurrence.datesBetween(anchor, RecurrenceRule.MONTHLY, d("2026-03-01"), d("2026-02-01"), endDate = null).isEmpty())
    }

    @Test
    fun datesBetween_farFromAnchor_isFast_andExact() {
        val dates = Recurrence.datesBetween(d("2000-01-15"), RecurrenceRule.DAILY, d("2026-11-12"), d("2026-11-14"), endDate = null)
        assertEquals(dates("2026-11-12", "2026-11-13", "2026-11-14"), dates)
    }

    @Test
    fun firstOnOrAfter() {
        assertEquals(d("2026-03-31"), Recurrence.firstOnOrAfter(d("2026-01-31"), RecurrenceRule.MONTHLY, d("2026-03-01")))
        assertEquals(d("2026-03-31"), Recurrence.firstOnOrAfter(d("2026-01-31"), RecurrenceRule.MONTHLY, d("2026-03-31")))
        assertEquals(d("2026-01-31"), Recurrence.firstOnOrAfter(d("2026-01-31"), RecurrenceRule.MONTHLY, d("2025-01-01")))
        assertEquals(d("2026-11-26"), Recurrence.firstOnOrAfter(d("2026-11-12"), RecurrenceRule.BIWEEKLY, d("2026-11-13")))
    }

    @Test
    fun horizonEnd_isLastDayTwelveMonthsAhead() {
        assertEquals(d("2027-11-30"), Recurrence.horizonEnd(d("2026-11-12")))
        assertEquals(d("2028-02-29"), Recurrence.horizonEnd(d("2027-02-01")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroInterval_isRejected() {
        RecurrenceRule(RecurrenceUnit.MONTH, 0)
    }

    @Test
    fun presets_areRecognized() {
        assertEquals(RecurrenceRule.Preset.BIWEEKLY, RecurrenceRule(RecurrenceUnit.WEEK, 2).presetOrNull())
        assertEquals(RecurrenceRule.Preset.SEMIANNUAL, RecurrenceRule(RecurrenceUnit.MONTH, 6).presetOrNull())
    }
}
