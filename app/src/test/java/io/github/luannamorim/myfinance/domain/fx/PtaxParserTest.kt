package io.github.luannamorim.myfinance.domain.fx

import io.github.luannamorim.myfinance.core.format.RateInput
import io.github.luannamorim.myfinance.data.rates.BcbRateClient
import io.github.luannamorim.myfinance.testutil.d
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

class PtaxParserTest {

    @Test
    fun realSample() {
        val parsed = PtaxParser.parse("""[{"data":"10/11/2026","valor":"5.5840"},{"data":"11/11/2026","valor":"5.5606"},{"data":"12/11/2026","valor":"5.6143"}]""")
        assertEquals(listOf(d("2026-11-10"), d("2026-11-11"), d("2026-11-12")), parsed.map { it.first })
        assertEquals(BigDecimal("5.6143"), parsed.last().second)
    }

    @Test
    fun emptyArray_isValid() {
        assertTrue(PtaxParser.parse("[]").isEmpty())
    }

    @Test
    fun malformedAndOutOfRangeEntries_areDropped() {
        val parsed = PtaxParser.parse(
            """[{"data":"2026-11-12","valor":"5.6"},{"data":"12/11/2026","valor":"0"},{"data":"12/11/2026","valor":"abc"},{"valor":"5.6"},{"data":"13/11/2026","valor":"5.60"}]""",
        )
        assertEquals(listOf(d("2026-11-13") to BigDecimal("5.60")), parsed)
    }

    @Test(expected = RateFormatException::class)
    fun html_isAnError() {
        PtaxParser.parse("<html><body>Maintenance</body></html>")
    }

    @Test(expected = RateFormatException::class)
    fun object_isAnError() {
        PtaxParser.parse("""{"erro":{"statusCode":500}}""")
    }

    @Test
    fun bcb404WithoutValues_isAnEmptyResult() {
        val body = """{"erro":{"statusCode":404,"detail":"br.gov.bcb.pec.sgs.comum.excecoes.SGSNegocioException: Value(s) not found"}}"""
        assertEquals("[]", BcbRateClient.interpret(404, body))
        assertEquals("[]", BcbRateClient.interpret(200, "[]"))
    }

    @Test(expected = IOException::class)
    fun otherHttpErrors_fail() {
        BcbRateClient.interpret(503, "Service Unavailable")
    }

    @Test
    fun url_carriesOnlyTheDateRange() {
        assertEquals(
            "https://api.bcb.gov.br/dados/serie/bcdata.sgs.1/dados?formato=json&dataInicial=13/10/2026&dataFinal=12/11/2026",
            BcbRateClient.url(d("2026-10-13"), d("2026-11-12")),
        )
    }

    @Test
    fun rateInput_acceptsCommaOrDot() {
        assertEquals(BigDecimal("5.6143"), RateInput.parse("5,6143"))
        assertEquals(BigDecimal("5.6143"), RateInput.parse(" 5.6143 "))
        assertNull(RateInput.parse("0"))
        assertNull(RateInput.parse("5,61437891"))
        assertNull(RateInput.parse("abc"))
    }
}
