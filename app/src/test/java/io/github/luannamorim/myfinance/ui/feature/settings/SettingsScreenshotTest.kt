package io.github.luannamorim.myfinance.ui.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.testutil.PHONE_TALL
import io.github.luannamorim.myfinance.testutil.snapBothThemes
import io.github.luannamorim.myfinance.ui.navigation.CategoryScope
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = PHONE_TALL)
class SettingsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val expenses = listOf(
        CategoryItem(1, null, "expense.rent", "key", false),
        CategoryItem(2, null, "expense.groceries", "shopping_basket", false),
        CategoryItem(3, "Pets", null, "pets", false),
        CategoryItem(4, null, "expense.gym", "fitness_center", true),
    )

    @Test
    fun settings() = compose.snapBothThemes("settings") {
        SettingsContent(SettingsUiState(loading = false), SettingsActions(onOpenLanguage = {}))
    }

    @Test
    fun categories_expense_showsLocalizedSeedNames() {
        compose.snapBothThemes("categories_expense") {
            CategoriesContent(CategoriesUiState(loading = false, scope = CategoryScope.EXPENSE, active = expenses.take(3), archived = expenses.drop(3)), CategoriesActions())
        }
        compose.onNodeWithText("Rent").assertIsDisplayed()
        compose.onNodeWithText("Pets").assertIsDisplayed()
        compose.onNodeWithText("Gym").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pt-rBR-w411dp-h1600dp-xxhdpi")
    fun categories_inPortuguese() {
        compose.setContent {
            FinanceTheme {
                CategoriesContent(CategoriesUiState(loading = false, scope = CategoryScope.EXPENSE, active = expenses.take(3)), CategoriesActions())
            }
        }
        compose.onNodeWithText("Aluguel").assertIsDisplayed()
        compose.onNodeWithText("Mercado").assertIsDisplayed()
    }

    @Test
    fun categories_assets_withEditor() = compose.snapBothThemes("categories_assets_editor") {
        CategoriesContent(
            CategoriesUiState(
                loading = false,
                scope = CategoryScope.ASSET,
                active = listOf(
                    CategoryItem(1, null, "asset.fixed_income", "receipt_long", false, AssetGroup.INVESTMENT),
                    CategoryItem(2, null, "asset.vehicles", "directions_car", false, AssetGroup.PERSONAL),
                ),
                editing = CategoryEdit(0, "Art", "category", AssetGroup.PERSONAL, archived = false),
            ),
            CategoriesActions(),
        )
    }
}
