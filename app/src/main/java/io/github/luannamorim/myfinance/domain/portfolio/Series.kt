package io.github.luannamorim.myfinance.domain.portfolio

import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Currency
import java.time.LocalDate
import java.time.YearMonth

enum class Period(val months: Long?) { M1(1), M3(3), M6(6), Y1(12), ALL(null) }

/**
 * A point of the portfolio charts. [value] and [invested] cover assets open at [date]; [gain] is the cumulative
 * gain of every asset with records up to [date] (closed assets keep their realized gain).
 */
data class SeriesPoint(val date: LocalDate, val value: Total, val invested: Total, val gain: Total)

object Series {

    /** Weekly dates plus today for 1M; month ends plus today otherwise. Never before [firstRecord]. */
    fun sampleDates(period: Period, firstRecord: LocalDate, today: LocalDate): List<LocalDate> {
        if (firstRecord.isAfter(today)) return emptyList()
        val periodStart = period.months?.let { today.minusMonths(it) } ?: firstRecord
        val start = maxOf(firstRecord, periodStart)
        val dates = if (period == Period.M1) {
            generateSequence(start) { it.plusWeeks(1) }.takeWhile { it.isBefore(today) }.toList()
        } else {
            generateSequence(YearMonth.from(start).atEndOfMonth()) { YearMonth.from(it).plusMonths(1).atEndOfMonth() }
                .takeWhile { it.isBefore(today) }
                .toList()
        }
        return dates + today
    }

    fun firstRecord(data: PortfolioData, group: AssetGroup? = null): LocalDate? =
        data.assets.filter { group == null || data.groupOf(it) == group }
            .mapNotNull { PortfolioCalculator.firstRecordDate(data.flowsOf(it), data.valuationsOf(it)) }
            .minOrNull()

    fun portfolio(data: PortfolioData, period: Period, today: LocalDate, to: Currency, converter: Converter, group: AssetGroup? = null): List<SeriesPoint> {
        val first = firstRecord(data, group) ?: return emptyList()
        val assets = data.assets.filter { group == null || data.groupOf(it) == group }
        return sampleDates(period, first, today).map { date ->
            var value = Total.zero(to)
            var invested = Total.zero(to)
            var gain = Total.zero(to)
            assets.forEach { asset ->
                val flows = data.flowsOf(asset)
                val s = PortfolioCalculator.snapshot(asset, flows, data.valuationsOf(asset), date) ?: return@forEach
                val converted = PortfolioCalculator.convertedSnapshot(s, flows, date, to, converter)
                gain += converted.gain
                if (!s.closed) {
                    value += converted.value
                    invested += converted.contributed - converted.withdrawn
                }
            }
            SeriesPoint(date, value, invested, gain)
        }
    }
}
