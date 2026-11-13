package io.github.luannamorim.myfinance.testutil

import io.github.luannamorim.myfinance.domain.model.AdjustmentKind
import io.github.luannamorim.myfinance.domain.model.CashAdjustment
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.ExchangeRate
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.Occurrence
import io.github.luannamorim.myfinance.domain.model.OccurrenceState
import io.github.luannamorim.myfinance.domain.model.RateSource
import java.math.BigDecimal
import java.time.LocalDate

fun d(text: String): LocalDate = LocalDate.parse(text)

/** R$ from a decimal string: brl("2550.00"). */
fun brl(major: String) = Money.of(BigDecimal(major), Currency.BRL)

/** US$ from a decimal string. */
fun usd(major: String) = Money.of(BigDecimal(major), Currency.USD)

private var nextId = 1L

fun pending(kind: EntryKind, expected: Money, due: String, category: Long = 1, name: String = "Item") = Occurrence(
    id = nextId++, templateId = null, scheduledDate = null, dueDate = d(due), name = name, categoryId = category,
    kind = kind, expected = expected, description = null, state = OccurrenceState.PENDING,
    actual = null, actualDate = null, note = null, customized = false,
)

fun confirmed(kind: EntryKind, expected: Money, due: String, actual: Money = expected, on: String = due, category: Long = 1, name: String = "Item") =
    pending(kind, expected, due, category, name).copy(state = OccurrenceState.CONFIRMED, actual = actual, actualDate = d(on))

fun opening(amount: Money, on: String) = CashAdjustment(nextId++, AdjustmentKind.OPENING, d(on), amount, null)

fun adjustment(amount: Money, on: String) = CashAdjustment(nextId++, AdjustmentKind.ADJUSTMENT, d(on), amount, null)

fun rate(on: String, value: String, source: RateSource = RateSource.BCB) = ExchangeRate(d(on), BigDecimal(value), source)
