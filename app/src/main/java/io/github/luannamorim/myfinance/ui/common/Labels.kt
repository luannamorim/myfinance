package io.github.luannamorim.myfinance.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.domain.model.AssetCategory
import io.github.luannamorim.myfinance.domain.model.Category

/** Localized names of the predefined categories, by seed key (CategorySeed). */
private val seedNames: Map<String, Int> = mapOf(
    "income.salary" to R.string.seed_income_salary,
    "income.freelance" to R.string.seed_income_freelance,
    "income.side_income" to R.string.seed_income_side_income,
    "income.dividends" to R.string.seed_income_dividends,
    "income.business_income" to R.string.seed_income_business_income,
    "income.other_income" to R.string.seed_income_other_income,
    "expense.housing" to R.string.seed_expense_housing,
    "expense.rent" to R.string.seed_expense_rent,
    "expense.electricity" to R.string.seed_expense_electricity,
    "expense.water" to R.string.seed_expense_water,
    "expense.internet" to R.string.seed_expense_internet,
    "expense.phone" to R.string.seed_expense_phone,
    "expense.groceries" to R.string.seed_expense_groceries,
    "expense.transportation" to R.string.seed_expense_transportation,
    "expense.healthcare" to R.string.seed_expense_healthcare,
    "expense.gym" to R.string.seed_expense_gym,
    "expense.subscriptions" to R.string.seed_expense_subscriptions,
    "expense.entertainment" to R.string.seed_expense_entertainment,
    "expense.education" to R.string.seed_expense_education,
    "expense.shopping" to R.string.seed_expense_shopping,
    "expense.credit_card" to R.string.seed_expense_credit_card,
    "expense.loans" to R.string.seed_expense_loans,
    "expense.other_expenses" to R.string.seed_expense_other_expenses,
    "asset.fixed_income" to R.string.seed_asset_fixed_income,
    "asset.stocks" to R.string.seed_asset_stocks,
    "asset.etfs" to R.string.seed_asset_etfs,
    "asset.reits" to R.string.seed_asset_reits,
    "asset.investment_funds" to R.string.seed_asset_investment_funds,
    "asset.cryptocurrencies" to R.string.seed_asset_cryptocurrencies,
    "asset.other_investments" to R.string.seed_asset_other_investments,
    "asset.business_ownership" to R.string.seed_asset_business_ownership,
    "asset.startup_equity" to R.string.seed_asset_startup_equity,
    "asset.real_estate" to R.string.seed_asset_real_estate,
    "asset.vehicles" to R.string.seed_asset_vehicles,
    "asset.loans_receivable" to R.string.seed_asset_loans_receivable,
    "asset.other_assets" to R.string.seed_asset_other_assets,
)

@StringRes
fun seedNameRes(seedKey: String?): Int? = seedKey?.let { seedNames[it] }

/** The user's name for the category, or the localized predefined name if it was never renamed. */
@Composable
fun Category.displayName(): String = name ?: seedNameRes(seedKey)?.let { stringResource(it) } ?: ""

@Composable
fun AssetCategory.displayName(): String = name ?: seedNameRes(seedKey)?.let { stringResource(it) } ?: ""
