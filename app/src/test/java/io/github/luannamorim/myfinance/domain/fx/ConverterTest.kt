package io.github.luannamorim.myfinance.domain.fx

import io.github.luannamorim.myfinance.domain.model.Currency.BRL
import io.github.luannamorim.myfinance.domain.model.Currency.USD
import io.github.luannamorim.myfinance.domain.model.RateSource
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.rate
import io.github.luannamorim.myfinance.testutil.usd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConverterTest {

    private val rates = ExchangeRates(
        listOf(
            rate("2026-11-12", "5.6143"), // Thursday
            rate("2026-11-13", "5.6000"), // Friday
            rate("2026-11-13", "5.7000", RateSource.MANUAL),
        ),
    )
    private val converter = Converter(rates)

    @Test
    fun weekend_usesLastBusinessDay_andManualWins() {
        // Saturday 14/11 → Friday's manual rate 5,70
        assertEquals(brl("570.00"), converter.convert(usd("100.00"), BRL, d("2026-11-14")))
        assertEquals(RateSource.MANUAL, rates.rateOn(d("2026-11-14"))!!.source)
        assertEquals(brl("561.43"), converter.convert(usd("100.00"), BRL, d("2026-11-12")))
    }

    @Test
    fun brlToUsd_divides() {
        assertEquals(usd("100.00"), converter.convert(brl("561.43"), USD, d("2026-11-12")))
    }

    @Test
    fun sameCurrency_andZero_needNoRate() {
        val empty = Converter(ExchangeRates.EMPTY)
        assertEquals(brl("1.23"), empty.convert(brl("1.23"), BRL, null))
        assertEquals(brl("0.00"), empty.convert(usd("0.00"), BRL, null))
    }

    @Test
    fun beforeFirstRate_isUnavailable_andTotalIncomplete() {
        assertNull(converter.convert(usd("10.00"), BRL, d("2026-11-01")))
        val total = converter.sum(listOf(usd("10.00") to d("2026-11-01"), brl("5.00") to null), BRL)
        assertTrue(total.incomplete)
        assertEquals(brl("5.00"), total.money)
    }

    @Test
    fun sum_roundsOnce() {
        // 3 × US$ 0,01 at 5,6143 = 3 × 0,056143 = 0,168429 → R$ 0,17 (per-item rounding would give 0,18)
        val total = converter.sum(List(3) { usd("0.01") to d("2026-11-12") }, BRL)
        assertEquals(brl("0.17"), total.money)
    }

    @Test
    fun latest_isUsedForNullDate() {
        assertEquals(brl("57.00"), converter.convert(usd("10.00"), BRL, null))
    }
}
