package io.github.luannamorim.myfinance.domain.model

import kotlinx.serialization.Serializable

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** How numbers are written: `1.234,56` (Brazil, default) or `1,234.56`. */
enum class NumberFormatStyle { COMMA_DECIMAL, DOT_DECIMAL }

/** How dates are written: `DD/MM/YYYY` (default) or `MM/DD/YYYY`. */
enum class DateFormatStyle { DMY, MDY }

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val numberFormat: NumberFormatStyle = NumberFormatStyle.COMMA_DECIMAL,
    val dateFormat: DateFormatStyle = DateFormatStyle.DMY,
    /** Currency preselected for new transactions, assets and balances. */
    val defaultCurrency: Currency = Currency.BRL,
    /** Currency every amount is shown in (Overview, Cash Flow, Portfolio, History). */
    val displayCurrency: Currency = Currency.BRL,
    /** Currencies the Overview flag switches between (Settings → Currency). Never empty. */
    val enabledCurrencies: Set<Currency> = Currency.entries.toSet(),
    /** Fetch USD/BRL PTAX rates from the Banco Central automatically. */
    val autoFetchRates: Boolean = true,
    val autoBackup: Boolean = true,
)

/**
 * Turns [currency] on or off for the display switch. The last one on can't be turned off; turning off
 * the display currency moves the display to the first one left on. Editors keep every currency.
 */
fun AppSettings.withCurrencyEnabled(currency: Currency, enabled: Boolean): AppSettings {
    val next = if (enabled) enabledCurrencies + currency else enabledCurrencies - currency
    if (next.isEmpty()) return this
    val display = if (displayCurrency in next) displayCurrency else Currency.entries.first { it in next }
    return copy(enabledCurrencies = next, displayCurrency = display)
}

/** The currency after [AppSettings.displayCurrency] among the enabled ones, wrapping; the same one when it is alone. */
fun AppSettings.nextDisplayCurrency(): Currency = nextCurrency(displayCurrency, enabledCurrencies)

/** The currency after [current] among [enabled], in [Currency.entries] order, wrapping; [current] when it is alone. */
fun nextCurrency(current: Currency, enabled: Set<Currency>): Currency {
    val on = Currency.entries.filter { it in enabled }
    if (on.isEmpty()) return current
    return on[(on.indexOf(current) + 1).mod(on.size)]
}
