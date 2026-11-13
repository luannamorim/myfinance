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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
 * Geometry of the MyFinance mark on a 24x24 grid: the portfolio's allocation ring, almost closed and
 * completed by the accent dot (the latest data point). One stroke, as in MyFit: an arc of radius 7.4
 * around (12, 12) that leaves a gap at the top right; the dot sits in the gap, on the circle, with a
 * 1-unit gap to each end of the stroke. Ink is centred on (12, 12).
 * Shared with the launcher, monochrome, splash and notification vectors (see docs/BRAND.md).
 */
internal object MarkGeometry {
    val center = Offset(12f, 12f)
    const val RADIUS = 7.4f

    /** Degrees, clockwise from 3 o'clock (the Compose and SVG convention). */
    const val START_ANGLE = -8.784f
    const val SWEEP_ANGLE = 287.568f
    val dot = Offset(17.233f, 6.767f)
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
        val r = MarkGeometry.RADIUS * s
        drawArc(
            color = lineColor,
            startAngle = MarkGeometry.START_ANGLE,
            sweepAngle = MarkGeometry.SWEEP_ANGLE,
            useCenter = false,
            topLeft = Offset(MarkGeometry.center.x * s - r, MarkGeometry.center.y * s - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = MarkGeometry.STROKE * s, cap = StrokeCap.Round),
        )
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
