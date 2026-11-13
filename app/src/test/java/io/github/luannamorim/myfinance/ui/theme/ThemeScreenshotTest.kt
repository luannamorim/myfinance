package io.github.luannamorim.myfinance.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.testutil.PHONE_TALL
import io.github.luannamorim.myfinance.testutil.snapBothThemes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = PHONE_TALL)
class ThemeScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun themeCatalog() = compose.snapBothThemes("theme_catalog") { ThemeCatalog() }
}

@Composable
private fun ThemeCatalog() {
    val c = FinanceTheme.colors
    val t = FinanceTheme.type
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            "background" to c.background, "surface" to c.surface, "surfaceRaised" to c.surfaceRaised,
            "outline" to c.outline, "textPrimary" to c.textPrimary, "textSecondary" to c.textSecondary,
            "textTertiary" to c.textTertiary, "accent" to c.accent, "accentMuted" to c.accentMuted,
            "danger" to c.danger, "warning" to c.warning,
        ).forEach { (name, color) -> Swatch(name, color) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            c.chart.forEach { Box(Modifier.size(28.dp).background(it, RoundedCornerShape(8.dp))) }
        }
        Text("Display 32", style = t.display, color = c.textPrimary)
        Text("Headline 24", style = t.headline, color = c.textPrimary)
        Text("Title — Rent", style = t.title, color = c.textPrimary)
        Text("Body — Monthly, due on the 5th.", style = t.body, color = c.textPrimary)
        Text("Secondary — paid 3 days ago", style = t.body, color = c.textSecondary)
        Text("LABEL / OVERLINE", style = t.overline, color = c.textSecondary)
        Text("R$ 182.430,15", style = t.numericHero, color = c.textPrimary)
        Text("US$ 1.100,00", style = t.numericLarge, color = c.textPrimary)
        Text("+R$ 1.175,73 · 2,40%", style = t.numeric, color = c.positive)
        Text("−R$ 2.550,00", style = t.numeric, color = c.negative)
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    val c = FinanceTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp).background(color, RoundedCornerShape(8.dp)))
        Text(name, style = FinanceTheme.type.body, color = c.textPrimary)
    }
}
