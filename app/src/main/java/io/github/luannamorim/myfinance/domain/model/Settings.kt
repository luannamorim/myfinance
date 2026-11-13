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
    /** Currency used for totals (Overview, Cash Flow, Portfolio). */
    val displayCurrency: Currency = Currency.BRL,
    /** Fetch USD/BRL PTAX rates from the Banco Central automatically. */
    val autoFetchRates: Boolean = true,
    val autoBackup: Boolean = true,
)
