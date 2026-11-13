package io.github.luannamorim.myfinance.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun of_roundsHalfEven() {
        assertEquals(100L, Money.of(BigDecimal("1.005"), Currency.BRL).minor)
        assertEquals(102L, Money.of(BigDecimal("1.015"), Currency.BRL).minor)
        assertEquals(-250L, Money.of(BigDecimal("-2.5"), Currency.USD).minor)
    }

    @Test
    fun arithmetic_sameCurrency() {
        val a = Money(100_000, Currency.BRL)
        val b = Money(31_240, Currency.BRL)
        assertEquals(Money(68_760, Currency.BRL), a - b)
        assertEquals(Money(131_240, Currency.BRL), a + b)
        assertEquals(Money(-100_000, Currency.BRL), -a)
        assertTrue(b < a)
        assertEquals(Money(131_240, Currency.BRL), listOf(a, b).sum(Currency.BRL))
        assertEquals(Money.zero(Currency.USD), emptyList<Money>().sum(Currency.USD))
    }

    @Test(expected = IllegalArgumentException::class)
    fun plus_acrossCurrencies_throws() {
        Money(1, Currency.BRL) + Money(1, Currency.USD)
    }

    @Test
    fun toBigDecimal_isExact() {
        assertEquals(BigDecimal("1234.56"), Money(123_456, Currency.BRL).toBigDecimal())
    }
}
