package io.github.luannamorim.myfinance.core.format

import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.NumberFormatStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class MoneyFormatterTest {

    private val comma = MoneyFormatter(NumberFormatStyle.COMMA_DECIMAL)
    private val dot = MoneyFormatter(NumberFormatStyle.DOT_DECIMAL)
    private val nb = NBSP
    private val minus = MINUS

    @Test
    fun format_followsNumberStyle() {
        assertEquals("R$${nb}1.234,56", comma.format(Money(123_456, Currency.BRL)))
        assertEquals("R$${nb}1,234.56", dot.format(Money(123_456, Currency.BRL)))
        assertEquals("US$${nb}1.000.000,00", comma.format(Money(100_000_000, Currency.USD)))
    }

    @Test
    fun format_signs() {
        assertEquals("${minus}US$${nb}0,50", comma.format(Money(-50, Currency.USD)))
        assertEquals("+R$${nb}10,00", comma.format(Money(1_000, Currency.BRL), signed = true))
        assertEquals("R$${nb}0,00", comma.format(Money(0, Currency.BRL), signed = true))
        assertEquals("${minus}R$${nb}2.550,00", comma.format(Money(-255_000, Currency.BRL), signed = true))
    }

    @Test
    fun formatNumber_withoutSymbol() {
        assertEquals("312,40", comma.formatNumber(31_240, Currency.BRL))
        assertEquals("0,03", comma.formatNumber(3, Currency.BRL))
    }

    @Test
    fun formatPercent_twoDecimals() {
        assertEquals("2,40%", comma.formatPercent(BigDecimal("0.024")))
        assertEquals("2.40%", dot.formatPercent(BigDecimal("0.024")))
        assertEquals("${minus}1,50%", comma.formatPercent(BigDecimal("-0.015")))
        assertEquals("+23,51%", comma.formatPercent(BigDecimal("0.235146"), signed = true))
    }

    @Test
    fun formatRate_fourDecimals() {
        assertEquals("5,6143", comma.formatRate(BigDecimal("5.6143")))
        assertEquals("5.6000", dot.formatRate(BigDecimal("5.6")))
    }

    @Test
    fun formatShort_axisLabels() {
        assertEquals("950", comma.formatShort(95_000, Currency.BRL))
        assertEquals("8k", comma.formatShort(800_000, Currency.BRL))
        assertEquals("12,4k", comma.formatShort(1_243_015, Currency.BRL))
        assertEquals("1,2M", comma.formatShort(123_000_000, Currency.BRL))
        assertEquals("${minus}12k", comma.formatShort(-1_200_000, Currency.BRL))
    }
}
