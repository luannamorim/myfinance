package io.github.luannamorim.myfinance.ui.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.core.format.Formats
import io.github.luannamorim.myfinance.domain.model.AppSettings
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.NumberFormatStyle
import io.github.luannamorim.myfinance.testutil.PHONE_TALL
import io.github.luannamorim.myfinance.testutil.snapBothThemes
import io.github.luannamorim.myfinance.ui.common.LocalFormats
import io.github.luannamorim.myfinance.ui.components.AmountDisplay
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = PHONE_TALL)
class SettingsFlowTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun changingNumberFormat_reformatsAmounts() {
        var settings by mutableStateOf(AppSettings())
        compose.setContent {
            FinanceTheme {
                CompositionLocalProvider(LocalFormats provides Formats.from(settings, Locale.US)) {
                    Column {
                        AmountDisplay(Money(123_456, Currency.BRL))
                        SettingsContent(SettingsUiState(loading = false, settings = settings), SettingsActions(onUpdate = { settings = it(settings) }))
                    }
                }
            }
        }
        compose.onNodeWithText("R$ 1.234,56").assertIsDisplayed()
        compose.onNodeWithText("1,234.56").performScrollTo().performClick()
        assertEquals(NumberFormatStyle.DOT_DECIMAL, settings.numberFormat)
        compose.onNodeWithText("R$ 1,234.56").assertIsDisplayed()
    }

    @Test
    fun export_asksBeforeWritingUnencryptedData() {
        var exported = false
        compose.setContent { FinanceTheme { SettingsContent(SettingsUiState(loading = false), SettingsActions(onExport = { exported = true })) } }
        compose.onNodeWithText("Export CSV").performScrollTo().performClick()
        compose.onNodeWithText("Export unencrypted data?").assertIsDisplayed()
        compose.onNodeWithText("Export").performClick()
        assertEquals(true, exported)
    }

    @Test
    fun screenshots_privacyAndAbout() {
        compose.snapBothThemes("privacy") { PrivacyScreen(onBack = {}) }
    }

    @Test
    fun screenshot_about() {
        compose.snapBothThemes("about") { AboutScreen(onBack = {}, versionName = "1.0.0") }
    }
}
