package io.github.luannamorim.myfinance.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import kotlin.math.atan2
import kotlin.math.hypot

/** One slice: [share] is 0..1; [key] identifies it for selection. */
data class DonutSlice(val key: Long, val share: Float, val color: Color, val label: String)

private const val GAP_DEGREES = 1.5f

/**
 * The slice under [angleDegrees], measured clockwise from 12 o'clock (0..360), as drawn by [DonutChart].
 */
internal fun hitSlice(slices: List<DonutSlice>, angleDegrees: Float): Long? {
    val angle = ((angleDegrees % 360f) + 360f) % 360f
    var start = 0f
    slices.forEach { slice ->
        val sweep = slice.share * 360f
        if (angle >= start && angle < start + sweep) return slice.key
        start += sweep
    }
    return null
}

/**
 * Allocation donut with the total in the centre. Slices start at 12 o'clock and go clockwise with a small gap;
 * the selected slice is drawn in the accent colour and thicker. Tapping a slice selects it, tapping the
 * centre (or the selected slice again) clears the selection.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    centerTitle: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
) {
    val c = FinanceTheme.colors
    val thickness = 22.dp
    val description = slices.joinToString { "${it.label} ${(it.share * 100).toInt()}%" }
    Box(modifier.size(size).semantics { contentDescription = "$centerTitle $centerValue. $description" }, contentAlignment = Alignment.Center) {
        val emphasis by animateFloatAsState(if (selected == null) 0f else 1f, tween(200), label = "donut")
        Canvas(
            Modifier.size(size).pointerInput(slices, selected) {
                detectTapGestures { offset ->
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    val radius = minOf(this.size.width, this.size.height) / 2f
                    if (hypot(dx, dy) < radius - thickness.toPx() * 1.6f) {
                        onSelect(null)
                    } else {
                        // atan2 measures from 3 o'clock counter-clockwise on screen axes; convert to clockwise from 12.
                        val degrees = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f + 360f) % 360f
                        val hit = hitSlice(slices, degrees)
                        onSelect(if (hit == selected) null else hit)
                    }
                }
            },
        ) {
            val stroke = thickness.toPx()
            val inset = stroke
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            if (slices.isEmpty()) {
                drawArc(c.surfaceRaised, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                return@Canvas
            }
            var start = -90f
            slices.forEach { slice ->
                val sweep = slice.share * 360f
                val isSelected = slice.key == selected
                val gap = if (slices.size > 1) GAP_DEGREES else 0f
                val width = if (isSelected) stroke * (1f + 0.35f * emphasis) else stroke
                val color = when {
                    isSelected -> c.accent
                    selected != null -> slice.color.copy(alpha = 1f - 0.45f * emphasis)
                    else -> slice.color
                }
                if (sweep > gap) {
                    drawArc(color, start + gap / 2, sweep - gap, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Butt))
                }
                start += sweep
            }
        }
        // Kept inside the hole (the selected slice grows inwards), shrinking rather than overflowing.
        Column(Modifier.widthIn(max = size - thickness * 4), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTitle, style = FinanceTheme.type.caption, color = c.textSecondary, textAlign = TextAlign.Center, maxLines = 1, autoSize = shrinkToFit(FinanceTheme.type.caption))
            val valueStyle = FinanceTheme.type.title.copy(fontFeatureSettings = "tnum")
            Text(centerValue, style = valueStyle, color = c.textPrimary, textAlign = TextAlign.Center, maxLines = 1, autoSize = shrinkToFit(valueStyle))
        }
    }
}
