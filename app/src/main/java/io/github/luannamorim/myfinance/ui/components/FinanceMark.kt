package io.github.luannamorim.myfinance.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Geist

/**
 * Geometry of the MyFinance mark on a 24x24 grid: an "F" whose arms rise in parallel like a progress
 * line. One continuous stroke (stem → top arm) plus the middle arm; the top arm ends at a detached
 * accent dot (the latest data point), 1 unit away, as in MyFit. Ink is centred on (12, 12).
 * Shared with the launcher, monochrome, splash and notification vectors (see docs/BRAND.md).
 */
internal object MarkGeometry {
    val strokes = listOf(
        listOf(Offset(5.4f, 20.2f), Offset(5.4f, 10.2f), Offset(13.4f, 6.7f)),
        listOf(Offset(5.4f, 15.2f), Offset(10.9f, 12.8f)),
    )
    val dot = Offset(17.61f, 4.86f)
    const val DOT_RADIUS = 2.3f
    const val STROKE = 2.6f
}

@Composable
fun FinanceMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    lineColor: Color = FinanceTheme.colors.textPrimary,
    dotColor: Color = FinanceTheme.colors.accent,
) {
    Canvas(modifier.size(size).semantics { contentDescription = "MyFinance" }) {
        val s = this.size.minDimension / 24f
        val stroke = Stroke(width = MarkGeometry.STROKE * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        MarkGeometry.strokes.forEach { points ->
            val path = Path().apply {
                points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * s, p.y * s) else lineTo(p.x * s, p.y * s) }
            }
            drawPath(path = path, color = lineColor, style = stroke)
        }
        drawCircle(dotColor, radius = MarkGeometry.DOT_RADIUS * s, center = MarkGeometry.dot * s)
    }
}

@Composable
fun FinanceWordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 22.sp, showMark: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showMark) FinanceMark(size = (fontSize.value * 1.25f).dp)
        Text(
            text = "myfinance",
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize,
            letterSpacing = (-0.04).em,
            color = FinanceTheme.colors.textPrimary,
        )
    }
}
