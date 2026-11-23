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
            autoFetchPrices = false,
            autoBackup = false,
        )
        repo.update { changed }
        assertEquals(changed, repo.current())

        val other = testSettings()
        other.importJson(repo.exportJson())
        assertEquals(changed, other.current())
    }

    @Test
    fun enabledCurrencies_default_isAll() = runBlocking {
        assertEquals(setOf(Currency.BRL, Currency.USD), testSettings().current().enabledCurrencies)
    }

    @Test
    fun enabledCurrencies_roundTrip() = runBlocking {
        val repo = testSettings()
        repo.update { it.copy(enabledCurrencies = setOf(Currency.USD), displayCurrency = Currency.USD) }
        assertEquals(setOf(Currency.USD), repo.current().enabledCurrencies)
    }

    @Test
    fun importJson_fromBeforeEnabledCurrencies_turnsAllOn() = runBlocking {
        // A settings.json written by 1.2.x, without the field.
        val old = """{"themeMode":"LIGHT","numberFormat":"COMMA_DECIMAL","dateFormat":"DMY","defaultCurrency":"BRL","displayCurrency":"USD","autoFetchRates":true,"autoBackup":true}"""
        val repo = testSettings()
        repo.importJson(old)
        assertEquals(setOf(Currency.BRL, Currency.USD), repo.current().enabledCurrencies)
        assertEquals(Currency.USD, repo.current().displayCurrency)
    }
}
