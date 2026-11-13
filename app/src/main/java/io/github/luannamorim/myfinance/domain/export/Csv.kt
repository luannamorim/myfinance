package io.github.luannamorim.myfinance.domain.export

import io.github.luannamorim.myfinance.domain.model.Money
import java.time.LocalDate

/**
 * RFC 4180 CSV: comma separated, CRLF lines, quotes doubled. Text cells that a spreadsheet would read as a
 * formula (`=`, `+`, `-`, `@`, tab, CR) get a leading `'` (formula injection). Amounts and dates are plain,
 * machine-readable values: `2500.00`, `2026-11-12`.
 */
object Csv {
    private val formulaStarts = setOf('=', '+', '-', '@', '\t', '\r')

    /** Escapes a free-text value (names, notes) against formula injection. */
    fun safeText(text: String?): String? = text?.let { if (it.firstOrNull() in formulaStarts) "'$it" else it }

    fun row(cells: List<String?>): String = cells.joinToString(",", postfix = "\r\n") { quote(it.orEmpty()) }

    fun amount(money: Money?): String? = money?.toBigDecimal()?.toPlainString()

    fun date(date: LocalDate?): String? = date?.toString()

    private fun quote(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
