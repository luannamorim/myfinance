package io.github.luannamorim.myfinance.ui.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.domain.model.AppSettings
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.testutil.PHONE_TALL
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings → Currency: which currencies the Overview flag switches between. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = PHONE_TALL)
class CurrencySettingsTest {

    @get:Rule val compose = createComposeRule()

    private fun show(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit = {}) = compose.setContent {
        FinanceTheme { CurrencySettingsContent(CurrencyUiState(loading = false, settings = settings), CurrencyActions(onUpdate = onUpdate)) }
    }

    @Test
    fun turningOffUsd_updatesSettings() {
        var result: AppSettings? = null
        show(AppSettings()) { transform -> result = transform(AppSettings()) }
        compose.onNodeWithText("US dollar · US$").performClick()
        assertEquals(setOf(Currency.BRL), result?.enabledCurrencies)
    }

    @Test
    fun lastCurrency_switchIsDisabled() {
        show(AppSettings(enabledCurrencies = setOf(Currency.BRL)))
        compose.onNodeWithText("Brazilian real · R$").assertIsNotEnabled()
    }

    @Test
    fun showTotalsIn_listsOnlyEnabled_defaultForNewEntriesListsAll() {
        show(AppSettings(enabledCurrencies = setOf(Currency.BRL)))
        // "Show totals in": R$ only. "Default for new entries": R$ and US$.
        compose.onAllNodesWithText("R$").assertCountEquals(2)
        compose.onAllNodesWithText("US$").assertCountEquals(1)
    }
}
