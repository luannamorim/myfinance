package io.github.luannamorim.myfinance.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {

    @Test
    fun turningOffTheLastCurrency_isRefused() {
        val onlyBrl = AppSettings(enabledCurrencies = setOf(Currency.BRL))
        assertEquals(onlyBrl, onlyBrl.withCurrencyEnabled(Currency.BRL, false))
    }

    @Test
    fun turningOffTheDisplayCurrency_movesTheDisplay() {
        val s = AppSettings(displayCurrency = Currency.BRL).withCurrencyEnabled(Currency.BRL, false)
        assertEquals(setOf(Currency.USD), s.enabledCurrencies)
        assertEquals(Currency.USD, s.displayCurrency)
    }

    @Test
    fun turningOffAnotherCurrency_keepsTheDisplay_andTheDefault() {
        val s = AppSettings(displayCurrency = Currency.BRL, defaultCurrency = Currency.USD).withCurrencyEnabled(Currency.USD, false)
        assertEquals(setOf(Currency.BRL), s.enabledCurrencies)
        assertEquals(Currency.BRL, s.displayCurrency)
        // Only the flag and "Show totals in" follow the setting; new entries can still default to US$.
        assertEquals(Currency.USD, s.defaultCurrency)
    }

    @Test
    fun turningOnAgain_addsItBack() {
        val s = AppSettings(enabledCurrencies = setOf(Currency.BRL)).withCurrencyEnabled(Currency.USD, true)
        assertEquals(setOf(Currency.BRL, Currency.USD), s.enabledCurrencies)
    }

    @Test
    fun nextDisplay_cycles() {
        assertEquals(Currency.USD, AppSettings(displayCurrency = Currency.BRL).nextDisplayCurrency())
        assertEquals(Currency.BRL, AppSettings(displayCurrency = Currency.USD).nextDisplayCurrency())
    }

    @Test
    fun nextDisplay_withOneOn_staysPut() {
        assertEquals(Currency.BRL, AppSettings(enabledCurrencies = setOf(Currency.BRL)).nextDisplayCurrency())
    }
}
