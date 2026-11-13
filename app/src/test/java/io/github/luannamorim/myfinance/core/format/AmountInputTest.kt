package io.github.luannamorim.myfinance.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountInputTest {

    @Test
    fun minorOf_fillsFromCents() {
        assertEquals(0L, AmountInput.minorOf(""))
        assertEquals(3L, AmountInput.minorOf("3"))
        assertEquals(31_240L, AmountInput.minorOf("31240"))
        assertEquals(9_999_999_999_999L, AmountInput.minorOf("9999999999999"))
    }

    @Test
    fun minorOf_rejectsInvalid() {
        assertNull(AmountInput.minorOf("12345678901234"))
        assertNull(AmountInput.minorOf("12,40"))
        assertNull(AmountInput.minorOf("-5"))
    }

    @Test
    fun digitsOf_roundTrips() {
        assertEquals("", AmountInput.digitsOf(0))
        assertEquals("250000", AmountInput.digitsOf(250_000))
        assertEquals(250_000L, AmountInput.minorOf(AmountInput.digitsOf(250_000)))
    }

    @Test
    fun sanitize_keepsDigitsOnly() {
        assertEquals("31240", AmountInput.sanitize("R$ 312,40"))
        assertEquals("5", AmountInput.sanitize("0005"))
        assertEquals("1234567890123", AmountInput.sanitize("12345678901234567"))
    }
}
