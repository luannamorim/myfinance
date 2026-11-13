package io.github.luannamorim.myfinance.data.prefs

import io.github.luannamorim.myfinance.domain.model.AppSettings
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.DateFormatStyle
import io.github.luannamorim.myfinance.domain.model.NumberFormatStyle
import io.github.luannamorim.myfinance.domain.model.ThemeMode
import io.github.luannamorim.myfinance.testutil.testSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun defaults_matchTheSpec() = runBlocking {
        val s = testSettings().current()
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertEquals(NumberFormatStyle.COMMA_DECIMAL, s.numberFormat)
        assertEquals(DateFormatStyle.DMY, s.dateFormat)
        assertEquals(Currency.BRL, s.defaultCurrency)
        assertEquals(Currency.BRL, s.displayCurrency)
        assertEquals(true, s.autoFetchRates)
        assertEquals(true, s.autoBackup)
    }

    @Test
    fun update_persistsEveryField_andJsonRoundTrips() = runBlocking {
        val repo = testSettings()
        val changed = AppSettings(
            themeMode = ThemeMode.LIGHT,
            numberFormat = NumberFormatStyle.DOT_DECIMAL,
            dateFormat = DateFormatStyle.MDY,
            defaultCurrency = Currency.USD,
            displayCurrency = Currency.USD,
            autoFetchRates = false,
            autoBackup = false,
        )
        repo.update { changed }
        assertEquals(changed, repo.current())

        val other = testSettings()
        other.importJson(repo.exportJson())
        assertEquals(changed, other.current())
    }
}
