package io.github.luannamorim.myfinance.domain.cash

import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.domain.model.AdjustmentKind
import io.github.luannamorim.myfinance.domain.model.CashAdjustment
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.Occurrence
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import java.time.LocalDate
import java.time.YearMonth

/** Totals of one month, in the display currency. */
data class MonthSummary(
    /** Received income in the month (actual) plus income still pending and due in the month (expected). */
    val expectedIncome: Total,
    val receivedIncome: Total,
    /** Paid expenses in the month (actual) plus expenses still pending and due in the month (expected). */
    val expectedExpenses: Total,
    val paidExpenses: Total,
    /** Pending expenses due before the month (or before today, whichever is earlier). */
    val overdueEarlier: Total,
    val overdueEarlierCount: Int,
    /** Transfers from cash into assets in the month (done and pending); neither income nor expense. */
    val transfersOut: Total,
    /** Transfers from assets back into cash in the month (done and pending). */
    val transfersIn: Total,
)

/**
 * Cash calculations (docs/ARCHITECTURE.md, "Cash"). Pure functions; each occurrence counts once: confirmed by
 * its actual amount on its actual date, pending by its expected amount, cancelled never.
 */
object CashCalculator {

    fun opening(ledger: CashLedger, currency: Currency): CashAdjustment? =
        ledger.adjustments.firstOrNull { it.kind == AdjustmentKind.OPENING && it.amount.currency == currency }

    /** Cash of [currency] at the end of [at]. */
    fun balance(currency: Currency, at: LocalDate, ledger: CashLedger): Money {
        val opening = opening(ledger, currency)
        val from = opening?.date
        var total = if (opening != null && !opening.date.isAfter(at)) opening.amount.minor else 0L
        fun inRange(date: LocalDate) = !date.isAfter(at) && (from == null || !date.isBefore(from))
        ledger.adjustments.forEach {
            if (it.kind == AdjustmentKind.ADJUSTMENT && it.amount.currency == currency && inRange(it.date)) total += it.amount.minor
        }
        ledger.occurrences.forEach {
            val actual = it.actual
            val date = it.actualDate
            if (it.state == OccurrenceState.CONFIRMED && actual != null && date != null && actual.currency == currency && inRange(date)) {
                total += if (it.kind.cashInflow) actual.minor else -actual.minor
            }
        }
        return Money(total, currency)
    }

    fun available(ledger: CashLedger, today: LocalDate): Map<Currency, Money> =
        Currency.entries.associateWith { balance(it, today, ledger) }

    /**
     * Available cash plus pending income and transfers in, minus pending expenses and transfers out, due on or
     * before [through] (overdue included).
     */
    fun projected(ledger: CashLedger, today: LocalDate, through: LocalDate): Map<Currency, Money> =
        Currency.entries.associateWith { currency ->
            var total = balance(currency, today, ledger).minor
            ledger.occurrences.forEach {
                if (it.state == OccurrenceState.PENDING && it.expected.currency == currency && !it.dueDate.isAfter(through)) {
                    total += if (it.kind.cashInflow) it.expected.minor else -it.expected.minor
                }
            }
            Money(total, currency)
        }

    /** Converts per-currency amounts at the latest rate (or at [on]). */
    fun consolidate(perCurrency: Map<Currency, Money>, to: Currency, converter: Converter, on: LocalDate? = null): Total =
        converter.sum(perCurrency.values.map { it to on }, to)

    /** The amount an occurrence contributes and the date its rate is taken from, or null if cancelled. */
    private fun Occurrence.effective(): Pair<Money, LocalDate?>? = when (state) {
        OccurrenceState.CONFIRMED -> actual?.let { it to actualDate }
        OccurrenceState.PENDING -> expected to null
        OccurrenceState.CANCELLED -> null
    }

    /** The month an occurrence belongs to: its actual date when confirmed, its due date otherwise. */
    fun monthOf(o: Occurrence): YearMonth = YearMonth.from(if (o.state == OccurrenceState.CONFIRMED) o.actualDate ?: o.dueDate else o.dueDate)

    fun monthSummary(ledger: CashLedger, month: YearMonth, today: LocalDate, to: Currency, converter: Converter): MonthSummary {
        val inMonth = ledger.occurrences.filter { it.state != OccurrenceState.CANCELLED && monthOf(it) == month }
        fun total(kind: EntryKind, confirmedOnly: Boolean) = converter.sum(
            inMonth.filter { it.kind == kind && (!confirmedOnly || it.state == OccurrenceState.CONFIRMED) }.mapNotNull { it.effective() },
            to,
        )
        val cutoff = minOf(month.atDay(1), today)
        val overdue = ledger.occurrences.filter {
            it.state == OccurrenceState.PENDING && it.kind == EntryKind.EXPENSE && it.dueDate.isBefore(cutoff)
        }
        return MonthSummary(
            expectedIncome = total(EntryKind.INCOME, confirmedOnly = false),
            receivedIncome = total(EntryKind.INCOME, confirmedOnly = true),
            expectedExpenses = total(EntryKind.EXPENSE, confirmedOnly = false),
            paidExpenses = total(EntryKind.EXPENSE, confirmedOnly = true),
            overdueEarlier = converter.sum(overdue.map { it.expected to null }, to),
            overdueEarlierCount = overdue.size,
            transfersOut = total(EntryKind.TRANSFER_OUT, confirmedOnly = false),
            transfersIn = total(EntryKind.TRANSFER_IN, confirmedOnly = false),
        )
    }

    /** Cash at the end of each month (all currencies, converted at that day's rate). */
    fun balanceHistory(ledger: CashLedger, months: List<YearMonth>, to: Currency, converter: Converter): List<Pair<YearMonth, Total>> =
        months.zip(balanceOn(ledger, months.map { it.atEndOfMonth() }, to, converter)) { month, (_, total) -> month to total }

    /** Cash at the end of each of [dates] (all currencies, converted at that day's rate). */
    fun balanceOn(ledger: CashLedger, dates: List<LocalDate>, to: Currency, converter: Converter): List<Pair<LocalDate, Total>> =
        dates.map { date -> date to converter.sum(Currency.entries.map { balance(it, date, ledger) to date }, to) }

    /** Totals per category for one month and kind (INCOME or EXPENSE; transfers have no category), largest first. */
    fun byCategory(ledger: CashLedger, month: YearMonth, kind: EntryKind, to: Currency, converter: Converter): List<Pair<Long, Total>> =
        ledger.occurrences
            .filter { it.kind == kind && it.state != OccurrenceState.CANCELLED && monthOf(it) == month }
            .groupBy { it.categoryId }
            .mapNotNull { (category, items) -> category?.let { it to converter.sum(items.mapNotNull { o -> o.effective() }, to) } }
            .sortedByDescending { it.second.money.minor }
}
