package io.github.luannamorim.myfinance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.luannamorim.myfinance.domain.model.ThemeMode
import io.github.luannamorim.myfinance.testutil.SCREENSHOT_DIR
import io.github.luannamorim.myfinance.testutil.TABLET
import io.github.luannamorim.myfinance.ui.components.FinanceMark
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.ui.theme.Geist
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The README header (docs/screenshots/brand.png): the symbol, large, and the name. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = TABLET)
class ReadmeHeaderScreenshotTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun header() {
        compose.setContent {
            FinanceTheme(ThemeMode.DARK) {
                Row(
                    Modifier.testTag("header").background(FinanceTheme.colors.background).padding(horizontal = 48.dp, vertical = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    FinanceMark(size = 120.dp)
                    Text(
                        "myfinance",
                        fontFamily = Geist,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 76.sp,
                        letterSpacing = (-0.04).em,
                        color = FinanceTheme.colors.textPrimary,
                    )
                }
            }
        }
        compose.onNodeWithTag("header").captureRoboImage("$SCREENSHOT_DIR/readme_header.png")
    }
}
