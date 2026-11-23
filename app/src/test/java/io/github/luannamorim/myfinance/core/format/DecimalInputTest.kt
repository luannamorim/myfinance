package io.github.luannamorim.myfinance.core.format

import io.github.luannamorim.myfinance.domain.model.NumberFormatStyle
import io.github.luannamorim.myfinance.domain.model.NumberFormatStyle.COMMA_DECIMAL
import io.github.luannamorim.myfinance.domain.model.NumberFormatStyle.DOT_DECIMAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class DecimalInputTest {

    private fun ok(expected: String, text: String, style: NumberFormatStyle) =
        assertEquals(BigDecimal(expected), DecimalInput.parse(text, style).getOrThrow())

    private fun bad(text: String, style: NumberFormatStyle) =
        assertTrue("$text should fail", DecimalInput.parse(text, style).isFailure)

    @Test
    fun commaDecimal_dotsAreOnlyThousandGroups() {
        ok("1150.00", "1.150,00", COMMA_DECIMAL)
        ok("121.28", "121,28", COMMA_DECIMAL)
        ok("0.00000379", "0,00000379", COMMA_DECIMAL)
        ok("50000000", "50.000.000", COMMA_DECIMAL)
        ok("1000", "1.000", COMMA_DECIMAL)
        ok("10", " 10 ", COMMA_DECIMAL)
        bad("1.5", COMMA_DECIMAL)
        bad("1,123456789", COMMA_DECIMAL)
        bad("1,2,3", COMMA_DECIMAL)
        bad("-1", COMMA_DECIMAL)
        bad("1.00.000", COMMA_DECIMAL)
        assertNull(DecimalInput.parse("", COMMA_DECIMAL).getOrThrow())
        assertNull(DecimalInput.parse("  ", COMMA_DECIMAL).getOrThrow())
    }

    @Test
    fun dotDecimal_commasAreOnlyThousandGroups() {
        ok("1150.00", "1,150.00", DOT_DECIMAL)
        ok("0.5", "0.5", DOT_DECIMAL)
        bad("1,5", DOT_DECIMAL)
    }

    @Test
    fun integerPart_cappedAtTwelveDigits() {
        ok("999999999999", "999999999999", COMMA_DECIMAL)
        ok("999999999999", "999.999.999.999", COMMA_DECIMAL)
        ok("999999999999.5", "999,999,999,999.5", DOT_DECIMAL)
        bad("1000000000000", COMMA_DECIMAL)
        bad("1.000.000.000.000", COMMA_DECIMAL)
        bad("1,000,000,000,000", DOT_DECIMAL)
    }

    @Test
    fun maxDecimals_isConfigurable() {
        assertTrue(DecimalInput.parse("1,255", COMMA_DECIMAL, maxDecimals = 2).isFailure)
        ok("1.25", "1,25", COMMA_DECIMAL)
    }
}
