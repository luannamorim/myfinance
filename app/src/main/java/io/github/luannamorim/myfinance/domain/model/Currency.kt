package io.github.luannamorim.myfinance.domain.model

/**
 * Supported currencies. Amounts are stored in minor units ([fractionDigits] decimal places).
 * Adding a currency means adding an entry here, exchange rates for it and its strings.
 */
enum class Currency(val code: String, val symbol: String, val fractionDigits: Int) {
    BRL("BRL", "R$", 2),
    USD("USD", "US$", 2),
}
