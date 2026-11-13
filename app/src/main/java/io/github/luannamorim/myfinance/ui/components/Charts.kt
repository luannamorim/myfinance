package io.github.luannamorim.myfinance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Geist
import io.github.luannamorim.myfinance.ui.theme.Spacing
import kotlin.math.max

/** One line of a line chart. Points are (x, y) in display units; x is usually an epoch day or a month index. */
data class LineSeries(val points: List<Pair<Double, Double>>, val color: Color, val area: Boolean = false)

@Composable
private fun axisLabel() = rememberAxisLabelComponent(
    style = TextStyle(color = FinanceTheme.colors.textTertiary, fontSize = 11.sp, fontFamily = Geist),
)

/**
 * Line chart drawn only through the given points (never interpolated beyond them). The model is built
 * synchronously so it renders on the first frame, as in MyFit. [summary] is read by screen readers.
 */
@Composable
fun FinanceLineChart(
    series: List<LineSeries>,
    xLabel: (Double) -> String,
    yLabel: (Double) -> String,
    summary: String,
    modifier: Modifier = Modifier,
    labelCount: Int = 4,
) {
    val c = FinanceTheme.colors
    val drawable = series.filter { it.points.isNotEmpty() }
    if (drawable.isEmpty()) return
    val model = remember(drawable) {
        CartesianChartModel(
            LineCartesianLayerModel.build { drawable.forEach { s -> series(s.points.map { it.first }, s.points.map { it.second }) } },
        )
    }
    val count = drawable.maxOf { it.points.size }
    val spacing = max(1, count / labelCount)
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    drawable.map { s ->
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(Fill(s.color)),
                            areaFill = if (s.area) {
                                LineCartesianLayer.AreaFill.single(Fill(Brush.verticalGradient(listOf(s.color.copy(alpha = 0.14f), Color.Transparent))))
                            } else {
                                null
                            },
                            pointProvider = if (count <= 24) {
                                LineCartesianLayer.PointProvider.single(LineCartesianLayer.Point(rememberShapeComponent(fill = Fill(s.color), shape = CircleShape), 6.dp))
                            } else {
                                null
                            },
                        )
                    },
                ),
            ),
            startAxis = VerticalAxis.rememberStart(
                line = null,
                tick = null,
                label = axisLabel(),
                guideline = rememberAxisGuidelineComponent(fill = Fill(c.outline)),
                valueFormatter = remember(yLabel) { CartesianValueFormatter { _, value, _ -> yLabel(value) } },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                line = rememberAxisLineComponent(fill = Fill(c.outline)),
                tick = null,
                guideline = null,
                label = axisLabel(),
                valueFormatter = remember(xLabel) { CartesianValueFormatter { _, value, _ -> xLabel(value) } },
                itemPlacer = remember(spacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { spacing }) },
            ),
        ),
        model = model,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        modifier = modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = summary },
    )
}

/** Grouped columns: each series is one colour, x is the group index (0..n−1). */
@Composable
fun FinanceColumnChart(
    series: List<Pair<List<Double>, Color>>,
    xLabel: (Int) -> String,
    yLabel: (Double) -> String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    val c = FinanceTheme.colors
    if (series.isEmpty() || series.all { it.first.isEmpty() }) return
    val model = remember(series) {
        CartesianChartModel(ColumnCartesianLayerModel.build { series.forEach { (values, _) -> series(values) } })
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    series.map { (_, color) -> rememberLineComponent(fill = Fill(color), thickness = 10.dp, shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)) },
                ),
            ),
            startAxis = VerticalAxis.rememberStart(
                line = null,
                tick = null,
                label = axisLabel(),
                guideline = rememberAxisGuidelineComponent(fill = Fill(c.outline)),
                valueFormatter = remember(yLabel) { CartesianValueFormatter { _, value, _ -> yLabel(value) } },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                line = rememberAxisLineComponent(fill = Fill(c.outline)),
                tick = null,
                guideline = null,
                label = axisLabel(),
                valueFormatter = remember(xLabel) { CartesianValueFormatter { _, value, _ -> xLabel(value.toInt()) } },
            ),
        ),
        model = model,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        modifier = modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = summary },
    )
}

/** A horizontal bar with label and value, for "by category" breakdowns. [share] is 0..1 of the largest item. */
data class BarItem(val label: String, val value: String, val share: Float)

@Composable
fun CategoryBars(items: List<BarItem>, color: Color, modifier: Modifier = Modifier) {
    val c = FinanceTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        items.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label, style = FinanceTheme.type.label, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.value, style = FinanceTheme.type.numeric, color = c.textSecondary)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).background(c.surfaceRaised, CircleShape)) {
                    Box(Modifier.fillMaxWidth(item.share.coerceIn(0.02f, 1f)).height(6.dp).background(color, CircleShape))
                }
            }
        }
    }
}

/** A small colour key: ● label. */
@Composable
fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = FinanceTheme.type.caption, color = FinanceTheme.colors.textSecondary)
    }
}
