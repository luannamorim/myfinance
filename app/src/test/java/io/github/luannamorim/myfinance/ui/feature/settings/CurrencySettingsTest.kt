package io.github.luannamorim.myfinance.ui.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.data.prefs.PriceError
import io.github.luannamorim.myfinance.data.prefs.PriceState
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

    private fun show(
        settings: AppSettings,
        priceState: PriceState = PriceState(),
        onUpdate: ((AppSettings) -> AppSettings) -> Unit = {},
    ) = compose.setContent {
        FinanceTheme {
            CurrencySettingsContent(
                CurrencyUiState(loading = false, settings = settings, priceState = priceState),
                CurrencyActions(onUpdate = onUpdate),
            )
        }
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

    @Test
    fun stocksSwitch_updatesSetting() {
        var result: AppSettings? = null
        show(AppSettings(autoFetchPrices = true), onUpdate = { transform -> result = transform(AppSettings(autoFetchPrices = true)) })
        compose.onNodeWithText("Fetch stock and crypto quotes").performClick()
        assertEquals(false, result?.autoFetchPrices)
    }

    @Test
    fun stocksError_shown() {
        show(AppSettings(), PriceState(lastError = PriceError.NETWORK))
        compose.onNodeWithText("Couldn't reach Yahoo Finance. The last quotes stay in use.").assertExists()
    }

    @Test
    @Config(qualifiers = "pt-rBR-w360dp-h1400dp-xhdpi")
    fun ptBr_showsStocksCard() {
        show(AppSettings())
        compose.onNodeWithText("Ações e cripto", ignoreCase = true).assertExists()
    }
}
