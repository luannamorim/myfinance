package io.github.luannamorim.myfinance.domain.networth

import io.github.luannamorim.myfinance.domain.cash.CashCalculator
import io.github.luannamorim.myfinance.domain.cash.CashLedger
import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.fx.Total
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioCalculator
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioData
import java.time.LocalDate

/** Net worth and how it is composed, in one currency. */
data class NetWorth(val cash: Total, val investments: Total, val personal: Total) {
    val total: Total get() = cash + investments + personal
}

/**
 * Net worth = cash (every currency) + investments + personal assets, each converted at the date's rate.
 * Portfolio flows never move cash (docs/ARCHITECTURE.md), so nothing is subtracted or added between them.
 */
object NetWorthCalculator {

    fun at(date: LocalDate, cash: CashLedger, portfolio: PortfolioData, to: Currency, converter: Converter): NetWorth = NetWorth(
        cash = converter.sum(Currency.entries.map { CashCalculator.balance(it, date, cash) to date }, to),
        investments = PortfolioCalculator.totals(portfolio, date, to, converter, AssetGroup.INVESTMENT).value,
        personal = PortfolioCalculator.totals(portfolio, date, to, converter, AssetGroup.PERSONAL).value,
    )

    fun series(dates: List<LocalDate>, cash: CashLedger, portfolio: PortfolioData, to: Currency, converter: Converter): List<Pair<LocalDate, NetWorth>> =
        dates.map { it to at(it, cash, portfolio, to, converter) }

    /** The first date anything was recorded (opening balance, adjustment, confirmation, asset record). */
    fun firstRecord(cash: CashLedger, portfolio: PortfolioData): LocalDate? {
        val cashDates = cash.adjustments.map { it.date } + cash.occurrences.mapNotNull { it.actualDate }
        val assetDates = portfolio.assets.mapNotNull { PortfolioCalculator.firstRecordDate(portfolio.flowsOf(it), portfolio.valuationsOf(it)) }
        return (cashDates + assetDates).minOrNull()
    }
}
