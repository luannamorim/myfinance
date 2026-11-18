package io.github.luannamorim.myfinance.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme

/**
 * The country flag of [currency], round, simplified to read at 24–32 dp. Coloured on purpose: here the
 * colour is the information (docs/BRAND.md keeps other icons neutral). A 1 dp ring keeps the white of the
 * US flag apart from a light background.
 */
@Composable
fun CurrencyFlag(currency: Currency, size: Dp, modifier: Modifier = Modifier) {
    val ring = FinanceTheme.colors.outlineStrong
    Canvas(modifier.size(size).clip(CircleShape).border(1.dp, ring, CircleShape)) {
        when (currency) {
            Currency.BRL -> drawBrazil()
            Currency.USD -> drawUnitedStates()
        }
    }
}

/** Drawn on a 32-unit grid. */
private fun DrawScope.u(units: Float) = units * size.width / 32f

private fun DrawScope.drawBrazil() {
    drawRect(Color(0xFF009C3B))
    val rhombus = Path().apply {
        moveTo(u(16f), u(5f)); lineTo(u(29f), u(16f)); lineTo(u(16f), u(27f)); lineTo(u(3f), u(16f)); close()
    }
    drawPath(rhombus, Color(0xFFFFDF00))
    val center = Offset(u(16f), u(16f))
    drawCircle(Color(0xFF002776), radius = u(6.4f), center = center)
    val globe = Path().apply { addOval(Rect(center, u(6.4f))) }
    clipPath(globe) {
        val band = Path().apply { moveTo(u(8.8f), u(14.2f)); quadraticTo(u(16f), u(11.6f), u(23.4f), u(16.6f)) }
        drawPath(band, Color.White, style = Stroke(width = u(1.5f)))
    }
}

private fun DrawScope.drawUnitedStates() {
    drawRect(Color.White)
    val stripe = size.height / 13f
    for (i in 0 until 13 step 2) {
        drawRect(Color(0xFFB22234), topLeft = Offset(0f, i * stripe), size = Size(size.width, stripe))
    }
    drawRect(Color(0xFF3C3B6E), size = Size(u(16.4f), 7 * stripe))
    val xs = listOf(2.6f, 6.2f, 9.8f, 13.4f)
    val ys = listOf(2.6f, 6.3f, 10f, 13.7f)
    ys.forEachIndexed { row, y ->
        val shift = if (row % 2 == 1) 1.8f else 0f
        xs.filter { it + shift < 15.2f }.forEach { x -> drawCircle(Color.White, radius = u(0.75f), center = Offset(u(x + shift), u(y))) }
    }
}
