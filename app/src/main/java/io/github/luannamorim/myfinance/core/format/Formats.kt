package io.github.luannamorim.myfinance.core.format

import io.github.luannamorim.myfinance.domain.model.AppSettings
import java.util.Locale

/** Formatters configured from the user's settings, provided to the UI through LocalFormats. */
data class Formats(val money: MoneyFormatter, val dates: DateFormats) {
    companion object {
        fun from(settings: AppSettings, locale: Locale = Locale.getDefault()) =
            Formats(MoneyFormatter(settings.numberFormat), DateFormats(settings.dateFormat, locale))
    }
}
