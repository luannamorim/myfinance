package io.github.luannamorim.myfinance.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.luannamorim.myfinance.MainActivity
import io.github.luannamorim.myfinance.testutil.SCREENSHOT_DIR
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-w411dp-h891dp-xxhdpi", application = HiltTestApplication::class)
class NavigationTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun heading(text: String) = compose.onNode(hasText(text) and isHeading())

    @Test
    fun eachTab_showsItsScreen_andBackReturnsToOverview() {
        heading("Overview").assertIsDisplayed()

        listOf("Cash Flow" to "Cash Flow", "Portfolio" to "Portfolio", "History" to "History", "Settings" to "Settings").forEach { (tab, title) ->
            compose.onAllNodesWithText(tab).onFirst().performClick()
            heading(title).assertIsDisplayed()
        }

        compose.onAllNodesWithText("Portfolio").onFirst().performClick()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        heading("Overview").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pt-rBR-w411dp-h891dp-xxhdpi")
    fun portuguese_labels() {
        heading("Resumo").assertIsDisplayed()
        compose.onNodeWithText("Carteira").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w840dp-h1200dp-xhdpi")
    fun expandedWidth_usesNavigationRail_screenshot() {
        compose.onNodeWithText("Settings").assertIsDisplayed()
        heading("Overview").assertIsDisplayed()
        compose.onRoot().captureRoboImage("$SCREENSHOT_DIR/nav_expanded_dark.png")
    }
}
