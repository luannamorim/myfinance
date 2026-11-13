package io.github.luannamorim.myfinance.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * MyFinance color tokens (same as MyFit). Graphite neutrals plus a single green accent reserved for
 * positive values and primary actions; red for overdue, negative and destructive. Values are
 * validated for WCAG contrast in ColorContrastTest.
 */
@Immutable
data class FinanceColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val outline: Color,
    val outlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentMuted: Color,
    val danger: Color,
    val warning: Color,
    /** Neutral ramp for chart series and categories, strongest first. The accent marks the selection. */
    val chart: List<Color>,
    val isDark: Boolean,
) {
    /** Income, gains and other positive values. */
    val positive: Color get() = accent

    /** Negative values, overdue items and destructive actions. */
    val negative: Color get() = danger
}

val DarkColors = FinanceColors(
    background = Color(0xFF0B0B0C),
    surface = Color(0xFF141416),
    surfaceRaised = Color(0xFF1C1C1F),
    outline = Color(0xFF2A2A2E),
    outlineStrong = Color(0xFF3A3A40),
    textPrimary = Color(0xFFF5F5F4),
    textSecondary = Color(0xFFA1A1A8),
    textTertiary = Color(0xFF6B6B73),
    accent = Color(0xFF47F55E),
    onAccent = Color(0xFF0B0B0C),
    accentMuted = Color(0x2447F55E),
    danger = Color(0xFFF2555A),
    warning = Color(0xFFF5A524),
    chart = listOf(
        Color(0xFFF5F5F4), Color(0xFFD0D0D4), Color(0xFFADADB4),
        Color(0xFF909099), Color(0xFF7A7A83), Color(0xFF66666E),
    ),
    isDark = true,
)

val LightColors = FinanceColors(
    background = Color(0xFFFAFAF9),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF2F2F0),
    outline = Color(0xFFE2E2DF),
    outlineStrong = Color(0xFFCBCBC7),
    textPrimary = Color(0xFF0B0B0C),
    textSecondary = Color(0xFF5A5A62),
    textTertiary = Color(0xFF8E8E95),
    accent = Color(0xFF15803D),
    onAccent = Color(0xFFFFFFFF),
    accentMuted = Color(0x1F15803D),
    danger = Color(0xFFCD2B31),
    warning = Color(0xFFB45309),
    chart = listOf(
        Color(0xFF0B0B0C), Color(0xFF34343A), Color(0xFF505058),
        Color(0xFF66666E), Color(0xFF7A7A82), Color(0xFF8C8C93),
    ),
    isDark = false,
)

val LocalFinanceColors = staticCompositionLocalOf { DarkColors }
