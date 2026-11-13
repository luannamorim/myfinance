package io.github.luannamorim.myfinance.core.format

import io.github.luannamorim.myfinance.domain.model.DateFormatStyle
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Date text. Numeric dates follow the user's [DateFormatStyle]; month names follow the UI [locale].
 */
class DateFormats(val style: DateFormatStyle, val locale: Locale) {

    private val full = DateTimeFormatter.ofPattern(if (style == DateFormatStyle.DMY) "dd/MM/yyyy" else "MM/dd/yyyy")
    private val dayMonth = DateTimeFormatter.ofPattern(if (style == DateFormatStyle.DMY) "dd/MM" else "MM/dd")
    private val monthYear = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    private val monthShort = DateTimeFormatter.ofPattern("LLL", locale)
    private val monthShortYear = DateTimeFormatter.ofPattern("LLL yy", locale)

    /** `12/11/2026` or `11/12/2026`. */
    fun date(date: LocalDate): String = full.format(date)

    /** `12/11` or `11/12`. */
    fun dayMonth(date: LocalDate): String = dayMonth.format(date)

    /** `November 2026` / `Novembro 2026`. */
    fun monthYear(month: YearMonth): String = monthYear.format(month).capitalized()

    /** `Nov` / `Nov` (no trailing period). */
    fun monthShort(month: YearMonth): String = monthShort.format(month).trimEnd('.').capitalized()

    /** `Nov 26` for chart axes spanning years. */
    fun monthShortYear(month: YearMonth): String = monthShortYear.format(month).replace(".", "").capitalized()

    private fun String.capitalized() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}
