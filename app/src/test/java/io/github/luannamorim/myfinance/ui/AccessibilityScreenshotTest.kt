package io.github.luannamorim.myfinance.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.domain.cash.CashLedger
import io.github.luannamorim.myfinance.domain.cash.HistoryFilter
import io.github.luannamorim.myfinance.domain.cash.RecurrenceRule
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.model.Asset
import io.github.luannamorim.myfinance.domain.model.AssetCategory
import io.github.luannamorim.myfinance.domain.model.AssetFlow
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.Category
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.Valuation
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioData
import io.github.luannamorim.myfinance.testutil.TABLET
import io.github.luannamorim.myfinance.testutil.brl
import io.github.luannamorim.myfinance.testutil.confirmed
import io.github.luannamorim.myfinance.testutil.d
import io.github.luannamorim.myfinance.testutil.opening
import io.github.luannamorim.myfinance.testutil.pending
import io.github.luannamorim.myfinance.testutil.rate
import io.github.luannamorim.myfinance.testutil.snapBothThemes
import io.github.luannamorim.myfinance.testutil.usd
import io.github.luannamorim.myfinance.ui.feature.asset.AssetDetailActions
import io.github.luannamorim.myfinance.ui.feature.asset.AssetDetailContent
import io.github.luannamorim.myfinance.ui.feature.asset.assetDetailState
import io.github.luannamorim.myfinance.ui.feature.cashflow.CashFlowActions
import io.github.luannamorim.myfinance.ui.feature.cashflow.CashFlowContent
import io.github.luannamorim.myfinance.ui.feature.cashflow.cashFlowState
import io.github.luannamorim.myfinance.ui.feature.history.HistoryContent
import io.github.luannamorim.myfinance.ui.feature.history.historyState
import io.github.luannamorim.myfinance.ui.feature.overview.OverviewActions
import io.github.luannamorim.myfinance.ui.feature.overview.OverviewContent
import io.github.luannamorim.myfinance.ui.feature.overview.overviewState
import io.github.luannamorim.myfinance.ui.feature.portfolio.PortfolioActions
import io.github.luannamorim.myfinance.ui.feature.portfolio.PortfolioContent
import io.github.luannamorim.myfinance.ui.feature.portfolio.portfolioState
import io.github.luannamorim.myfinance.ui.feature.settings.SettingsActions
import io.github.luannamorim.myfinance.ui.feature.settings.SettingsContent
import io.github.luannamorim.myfinance.ui.feature.settings.SettingsUiState
import io.github.luannamorim.myfinance.ui.feature.transaction.RepeatChoice
import io.github.luannamorim.myfinance.ui.feature.transaction.TransactionEditorActions
import io.github.luannamorim.myfinance.ui.feature.transaction.TransactionEditorContent
import io.github.luannamorim.myfinance.ui.feature.transaction.TransactionEditorUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/** Large fonts on a small phone, and the tablet layout: nothing clipped, amounts readable aloud. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h1400dp-xhdpi", fontScale = 1.5f)
class AccessibilityScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private val today = d("2026-11-12")
    private val categories = listOf(
        Category(10, null, EntryKind.INCOME, "work", "income.salary", false, 0),
        Category(20, null, EntryKind.EXPENSE, "key", "expense.rent", false, 1),
        Category(30, null, EntryKind.EXPENSE, "bolt", "expense.electricity", false, 2),
    )
    private val cash = CashLedger(
        adjustments = listOf(opening(brl("10000.00"), "2026-11-01"), opening(usd("500.00"), "2026-11-01")),
        occurrences = listOf(
            confirmed(EntryKind.INCOME, brl("9000.00"), "2026-11-05", category = 10, name = "Salary"),
            confirmed(EntryKind.EXPENSE, brl("2500.00"), "2026-11-10", actual = brl("2550.00"), category = 20, name = "Rent"),
            pending(EntryKind.EXPENSE, brl("250.00"), "2026-11-20", category = 30, name = "Electricity"),
            pending(EntryKind.EXPENSE, brl("120.00"), "2026-10-25", category = 30, name = "Internet"),
        ),
    )
    private val assetCategories = listOf(
        AssetCategory(1, null, AssetGroup.INVESTMENT, "receipt_long", "asset.fixed_income", false, 0),
        AssetCategory(2, null, AssetGroup.PERSONAL, "directions_car", "asset.vehicles", false, 0),
    )
    private val tesouro = Asset(10, "Tesouro Selic 2029", 1, Currency.BRL, null, null, d("2026-01-10"), null, null, null)
    private val car = Asset(11, "Car", 2, Currency.BRL, null, null, d("2026-01-10"), null, null, null)
    private val portfolio = PortfolioData(
        assets = listOf(tesouro, car),
        flows = mapOf(
            tesouro.id to listOf(AssetFlow(1, tesouro.id, FlowKind.INITIAL, d("2026-01-10"), brl("85000.00"), null, null)),
            car.id to listOf(AssetFlow(2, car.id, FlowKind.INITIAL, d("2026-01-10"), brl("45000.00"), null, null)),
        ),
        valuations = mapOf(
            tesouro.id to listOf(Valuation(3, tesouro.id, d("2026-10-31"), brl("91240.00"), null)),
            car.id to listOf(Valuation(4, car.id, d("2026-10-01"), brl("40000.00"), null)),
        ),
        categories = assetCategories,
    )
    private val rates = ExchangeRates(listOf(rate("2026-11-12", "5.6143")))

    @Test
    fun overview_largeFont() {
        compose.snapBothThemes("a11y_overview_font150") { OverviewContent(overviewState(cash, portfolio, categories, rates, Currency.BRL, today), OverviewActions()) }
        // Amounts are spoken with "minus" rather than the symbol.
        compose.onNodeWithContentDescription("minus R$ 2.550,00").assertExists()
    }

    @Test
    fun cashFlow_largeFont() {
        compose.snapBothThemes("a11y_cashflow_font150") {
            CashFlowContent(cashFlowState(cash, categories, rates, Currency.BRL, YearMonth.of(2026, 11), today), CashFlowActions())
        }
        compose.onNodeWithText("Internet").assertIsDisplayed()
    }

    @Test
    fun portfolio_largeFont() {
        compose.snapBothThemes("a11y_portfolio_font150") { PortfolioContent(portfolioState(portfolio, rates, Currency.BRL, null, null, today), PortfolioActions()) }
    }

    @Test
    fun assetDetail_largeFont() {
        compose.snapBothThemes("a11y_asset_detail_font150") {
            AssetDetailContent(assetDetailState(portfolio, tesouro.id, rates, Currency.BRL, today) { null }, AssetDetailActions())
        }
    }

    @Test
    fun history_largeFont() {
        compose.snapBothThemes("a11y_history_font150") { HistoryContent(historyState(cash, categories, HistoryFilter(), emptyMap(), today), {}, {}, {}) }
    }

    @Test
    fun transactionEditor_largeFont() {
        compose.snapBothThemes("a11y_transaction_editor_font150") {
            TransactionEditorContent(
                TransactionEditorUiState(
                    loading = false, name = "Rent", digits = "250000", categoryId = 20, categories = categories.filter { it.kind == EntryKind.EXPENSE },
                    date = d("2026-11-05"), today = today, repeat = RepeatChoice.Preset(RecurrenceRule.Preset.MONTHLY),
                ),
                TransactionEditorActions(),
            )
        }
    }

    @Test
    fun settings_largeFont() {
        compose.snapBothThemes("a11y_settings_font150") { SettingsContent(SettingsUiState(loading = false), SettingsActions()) }
    }

    @Test
    @Config(qualifiers = "pt-rBR-w360dp-h1400dp-xhdpi")
    fun overview_portuguese_largeFont() {
        compose.snapBothThemes("a11y_overview_pt_font150") { OverviewContent(overviewState(cash, portfolio, categories, rates, Currency.BRL, today), OverviewActions()) }
        compose.onNodeWithText("Resumo").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pt-rBR-w360dp-h1400dp-xhdpi")
    fun portfolio_portuguese_largeFont() {
        compose.snapBothThemes("a11y_portfolio_pt_font150") { PortfolioContent(portfolioState(portfolio, rates, Currency.BRL, null, null, today), PortfolioActions()) }
    }

    @Test
    @Config(qualifiers = TABLET, fontScale = 1.0f)
    fun overview_tablet() {
        compose.snapBothThemes("a11y_overview_tablet") { OverviewContent(overviewState(cash, portfolio, categories, rates, Currency.BRL, today), OverviewActions()) }
        compose.onNodeWithText("Overview").assertIsDisplayed()
    }
}
