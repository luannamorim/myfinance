package io.github.luannamorim.myfinance.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import io.github.luannamorim.myfinance.ui.theme.Spacing
import kotlin.math.max

/**
 * [main] on the left and [trailing] (usually an amount) on the right, vertically centred. When the trailing
 * block would leave [main] less than [minMainFraction] of the width (large fonts, long amounts), it moves
 * below [main], aligned to the end, instead of squeezing the text into a narrow column.
 */
@Composable
fun AdaptiveRow(
    main: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = Spacing.s,
    minMainFraction: Float = 0.4f,
) {
    Layout(contents = listOf(main, trailing), modifier = modifier) { (mainMeasurables, trailingMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val gap = spacing.roundToPx()
        val trailingPlaceables = trailingMeasurables.map { it.measure(loose) }
        val trailingWidth = trailingPlaceables.maxOfOrNull { it.width } ?: 0
        val trailingHeight = trailingPlaceables.sumOf { it.height }
        val sideBySide = !constraints.hasBoundedWidth || width - trailingWidth - gap >= width * minMainFraction
        if (sideBySide) {
            val mainMax = if (constraints.hasBoundedWidth) width - trailingWidth - gap else Constraints.Infinity
            val mainPlaceables = mainMeasurables.map { it.measure(loose.copy(maxWidth = mainMax)) }
            val mainWidth = mainPlaceables.maxOfOrNull { it.width } ?: 0
            val mainHeight = mainPlaceables.sumOf { it.height }
            val layoutWidth = if (constraints.hasBoundedWidth) width else mainWidth + gap + trailingWidth
            val height = max(mainHeight, trailingHeight).coerceAtLeast(constraints.minHeight)
            layout(layoutWidth, height) {
                var y = (height - mainHeight) / 2
                mainPlaceables.forEach { it.placeRelative(0, y); y += it.height }
                y = (height - trailingHeight) / 2
                trailingPlaceables.forEach { it.placeRelative(layoutWidth - it.width, y); y += it.height }
            }
        } else {
            val mainPlaceables = mainMeasurables.map { it.measure(loose.copy(maxWidth = width)) }
            val mainHeight = mainPlaceables.sumOf { it.height }
            val height = (mainHeight + gap / 2 + trailingHeight).coerceAtLeast(constraints.minHeight)
            layout(width, height) {
                var y = 0
                mainPlaceables.forEach { it.placeRelative(0, y); y += it.height }
                y += gap / 2
                trailingPlaceables.forEach { it.placeRelative(width - it.width, y); y += it.height }
            }
        }
    }
}

/**
 * Two tiles side by side with equal heights, or stacked when either one would need to shrink its content by
 * more than about a fifth to fit half the width (large fonts, long amounts).
 */
@Composable
fun TilePair(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = Spacing.m,
) {
    Layout(contents = listOf(first, second), modifier = modifier) { measurables, constraints ->
        val items = measurables.flatten()
        val width = constraints.maxWidth
        val gap = spacing.roundToPx()
        val half = (width - gap) / 2
        val fits = constraints.hasBoundedWidth && items.all { it.maxIntrinsicWidth(Constraints.Infinity) <= half * 6 / 5 }
        if (fits) {
            val height = items.maxOf { it.maxIntrinsicHeight(half) }
            val placeables = items.map { it.measure(Constraints.fixed(half, height)) }
            layout(width, height) {
                placeables.forEachIndexed { i, p -> p.placeRelative(i * (half + gap), 0) }
            }
        } else {
            val full = if (constraints.hasBoundedWidth) width else items.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
            val placeables = items.map { it.measure(Constraints(minWidth = full, maxWidth = full)) }
            val height = placeables.sumOf { it.height } + gap * (placeables.size - 1)
            layout(full, height) {
                var y = 0
                placeables.forEach { it.placeRelative(0, y); y += it.height + gap }
            }
        }
    }
}
