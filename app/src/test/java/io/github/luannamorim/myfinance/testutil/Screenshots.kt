package io.github.luannamorim.myfinance.testutil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import io.github.luannamorim.myfinance.domain.model.ThemeMode

const val SCREENSHOT_DIR = "build/outputs/roborazzi"

/** Phone-sized device for screenshot tests (Robolectric qualifiers). */
const val PHONE = "w411dp-h891dp-xxhdpi"
const val PHONE_TALL = "w411dp-h1600dp-xxhdpi"
const val TABLET = "w840dp-h1200dp-xhdpi"

/**
 * Renders [content] inside FinanceTheme and captures it in dark and light themes.
 * Captures are no-ops unless Roborazzi recording is enabled (recordRoborazziDebug).
 */
fun ComposeContentTestRule.snapBothThemes(name: String, content: @Composable () -> Unit) {
    var mode by mutableStateOf(ThemeMode.DARK)
    setContent {
        FinanceTheme(mode) {
            Box(Modifier.background(FinanceTheme.colors.background)) { content() }
        }
    }
    onRoot().captureRoboImage("$SCREENSHOT_DIR/${name}_dark.png")
    mode = ThemeMode.LIGHT
    waitForIdle()
    onRoot().captureRoboImage("$SCREENSHOT_DIR/${name}_light.png")
}
