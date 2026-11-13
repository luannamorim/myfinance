package io.github.luannamorim.myfinance.domain.export

import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.usd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsvTest {

    @Test
    fun quoting_followsRfc4180() {
        assertEquals("a,b\r\n", Csv.row(listOf("a", "b")))
        assertEquals("\"Rent, apt 2\",\"say \"\"hi\"\"\",\"two\nlines\",\r\n", Csv.row(listOf("Rent, apt 2", "say \"hi\"", "two\nlines", null)))
    }

    @Test
    fun formulaInjection_isNeutralized() {
        assertEquals("'=SUM(1)", Csv.safeText("=SUM(1)"))
        assertEquals("'+55 11", Csv.safeText("+55 11"))
        assertEquals("'-5", Csv.safeText("-5"))
        assertEquals("'@cmd", Csv.safeText("@cmd"))
        assertEquals("Rent", Csv.safeText("Rent"))
        assertNull(Csv.safeText(null))
    }

    @Test
    fun amountsAndDates_areMachineReadable() {
        assertEquals("2500.00", Csv.amount(brl("2500.00")))
        assertEquals("-37.60", Csv.amount(brl("-37.60")))
        assertEquals("0.05", Csv.amount(usd("0.05")))
        assertEquals("2026-11-12", Csv.date(d("2026-11-12")))
    }
}
