package io.github.luannamorim.myfinance.domain.market

import java.math.BigDecimal
import java.time.LocalDate
import java.util.TreeMap

/** Ticker symbols the app can track: US stocks/ETFs (`BRK-B`) and USD crypto pairs (`BTC-USD`). */
object Ticker {
    private val STOCK = Regex("^[A-Z0-9]{1,6}(-[A-Z]{1,2})?$")
    private val CRYPTO = Regex("^[A-Z0-9]{1,12}-USD$")
    private const val CRYPTO_SUFFIX = "-USD"

    /** Trimmed, uppercase symbol, or null when it is neither a stock/ETF nor a USD crypto symbol. */
    fun normalize(raw: String): String? {
        val symbol = raw.trim().uppercase()
        return symbol.takeIf { STOCK.matches(it) || CRYPTO.matches(it) }
    }

    fun isCrypto(symbol: String): Boolean = symbol.endsWith(CRYPTO_SUFFIX)

    /** The coin code of a crypto symbol ("BTC-USD" → "BTC"); null for stocks. */
    fun unitCode(symbol: String): String? = if (isCrypto(symbol)) symbol.removeSuffix(CRYPTO_SUFFIX) else null
}

/** A closing price as traded on [date] (not split-adjusted). [fetchedAtMs] is when it was last fetched. */
data class PricePoint(val date: LocalDate, val price: BigDecimal, val fetchedAtMs: Long? = null)

/** A stock split on [date]: each share becomes numerator ÷ denominator shares (10/1 is a 10-for-1 split). */
data class Split(val date: LocalDate, val numerator: BigDecimal, val denominator: BigDecimal)

/** Prices and splits of one symbol. */
data class SymbolHistory(val prices: List<PricePoint>, val splits: List<Split>) {

    private val byDate = TreeMap<LocalDate, PricePoint>().apply { prices.forEach { put(it.date, it) } }

    /** The latest price on or before [date] (weekends and holidays use the last close); null when there is none. */
    fun priceOn(date: LocalDate): PricePoint? = byDate.floorEntry(date)?.value
}

/** Quote histories by symbol. No price is ever invented. */
class MarketData(histories: Map<String, SymbolHistory>) {

    private val bySymbol = histories.toMap()

    fun priceOn(symbol: String, date: LocalDate): PricePoint? = bySymbol[symbol]?.priceOn(date)

    /** The splits of [symbol] sorted by date; empty when the symbol is unknown. */
    fun splitsOf(symbol: String): List<Split> = bySymbol[symbol]?.splits.orEmpty().sortedBy { it.date }

    companion object {
        val EMPTY = MarketData(emptyMap())
    }
}
