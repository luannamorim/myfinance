package io.github.luannamorim.myfinance.core.format

import io.github.luannamorim.myfinance.domain.model.DateFormatStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class DateFormatsTest {

    private val day = LocalDate.of(2026, 11, 12)
    private val ptBr = Locale.forLanguageTag("pt-BR")

    @Test
    fun numericDates_followStyle() {
        assertEquals("12/11/2026", DateFormats(DateFormatStyle.DMY, Locale.US).date(day))
        assertEquals("11/12/2026", DateFormats(DateFormatStyle.MDY, Locale.US).date(day))
        assertEquals("12/11", DateFormats(DateFormatStyle.DMY, Locale.US).dayMonth(day))
        assertEquals("11/12", DateFormats(DateFormatStyle.MDY, Locale.US).dayMonth(day))
    }

    @Test
    fun monthNames_followLocale() {
        val nov = YearMonth.of(2026, 11)
        assertEquals("November 2026", DateFormats(DateFormatStyle.DMY, Locale.US).monthYear(nov))
        assertEquals("Novembro 2026", DateFormats(DateFormatStyle.DMY, ptBr).monthYear(nov))
        assertEquals("Nov", DateFormats(DateFormatStyle.DMY, Locale.US).monthShort(nov))
        assertEquals("Nov", DateFormats(DateFormatStyle.DMY, ptBr).monthShort(nov))
    }
}
