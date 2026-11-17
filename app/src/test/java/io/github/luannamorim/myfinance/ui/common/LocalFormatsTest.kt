package io.github.luannamorim.myfinance.ui.common

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Without a provider (tests, previews), LocalFormats follows the UI language of each composition.
 * Both cases run in the same sandbox, so a default fixed by whichever test ran first fails one of them.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class LocalFormatsTest {

    @get:Rule val compose = createComposeRule()

    private val day = LocalDate.of(2026, 11, 12)

    @Test
    @Config(qualifiers = "en")
    fun default_inEnglish() {
        compose.setContent { Text(LocalFormats.current.dates.longDate(day)) }
        compose.onNodeWithText("November 12, 2026").assertExists()
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun default_inPortuguese() {
        compose.setContent { Text(LocalFormats.current.dates.longDate(day)) }
        compose.onNodeWithText("12 de novembro de 2026").assertExists()
    }
}
