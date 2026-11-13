package io.github.luannamorim.myfinance.data.db.seed

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.luannamorim.myfinance.domain.model.AssetGroup
import io.github.luannamorim.myfinance.domain.model.EntryKind

/**
 * Predefined categories, inserted when the database is created. Rows are stored with `name = NULL` and shown
 * with the localized string of their [SeedCategory.key] until the user renames them. Keys are part of the data
 * (backups, string lookups) and are frozen in `src/test/resources/seed-keys.txt`: never rename one.
 */
object CategorySeed {

    data class SeedCategory(val key: String, val englishName: String, val icon: String)

    val income = listOf(
        SeedCategory("income.salary", "Salary", "work"),
        SeedCategory("income.freelance", "Freelance", "laptop"),
        SeedCategory("income.side_income", "Side Income", "handyman"),
        SeedCategory("income.dividends", "Dividends", "savings"),
        SeedCategory("income.business_income", "Business Income", "storefront"),
        SeedCategory("income.other_income", "Other Income", "add_circle"),
    )

    val expense = listOf(
        SeedCategory("expense.housing", "Housing", "home"),
        SeedCategory("expense.rent", "Rent", "key"),
        SeedCategory("expense.electricity", "Electricity", "bolt"),
        SeedCategory("expense.water", "Water", "water_drop"),
        SeedCategory("expense.internet", "Internet", "wifi"),
        SeedCategory("expense.phone", "Phone", "smartphone"),
        SeedCategory("expense.groceries", "Groceries", "shopping_basket"),
        SeedCategory("expense.transportation", "Transportation", "directions_bus"),
        SeedCategory("expense.healthcare", "Healthcare", "medical_services"),
        SeedCategory("expense.gym", "Gym", "fitness_center"),
        SeedCategory("expense.subscriptions", "Subscriptions", "subscriptions"),
        SeedCategory("expense.entertainment", "Entertainment", "movie"),
        SeedCategory("expense.education", "Education", "school"),
        SeedCategory("expense.shopping", "Shopping", "shopping_bag"),
        // Added in 1.1.0 (Migrations.MIGRATION_1_2 adds them to existing databases).
        SeedCategory("expense.credit_card", "Credit Card", "credit_card"),
        SeedCategory("expense.loans", "Loans", "account_balance"),
        SeedCategory("expense.other_expenses", "Other Expenses", "more_horiz"),
    )

    val investments = listOf(
        SeedCategory("asset.fixed_income", "Fixed Income", "receipt_long"),
        SeedCategory("asset.stocks", "Stocks", "show_chart"),
        SeedCategory("asset.etfs", "ETFs", "stacked_line_chart"),
        SeedCategory("asset.reits", "REITs", "apartment"),
        SeedCategory("asset.investment_funds", "Investment Funds", "pie_chart"),
        SeedCategory("asset.cryptocurrencies", "Cryptocurrencies", "currency_bitcoin"),
        SeedCategory("asset.other_investments", "Other Investments", "trending_up"),
    )

    val personal = listOf(
        SeedCategory("asset.business_ownership", "Business Ownership", "business_center"),
        SeedCategory("asset.startup_equity", "Startup Equity", "rocket_launch"),
        SeedCategory("asset.real_estate", "Real Estate", "home_work"),
        SeedCategory("asset.vehicles", "Vehicles", "directions_car"),
        // Added in 1.1.0 (Migrations.MIGRATION_1_2 adds it to existing databases).
        SeedCategory("asset.loans_receivable", "Loans Receivable", "handshake"),
        SeedCategory("asset.other_assets", "Other Assets", "category"),
    )

    val allKeys: List<String> get() = (income + expense + investments + personal).map { it.key }

    /** English name of a predefined category (CSV export of never-renamed categories). */
    fun defaultName(seedKey: String): String? =
        (income + expense + investments + personal).firstOrNull { it.key == seedKey }?.englishName

    class Callback(private val nowMs: () -> Long = System::currentTimeMillis) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.beginTransaction()
            try {
                insertAll(db, nowMs())
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    internal fun insertAll(db: SupportSQLiteDatabase, nowMs: Long) {
        fun cash(items: List<SeedCategory>, kind: EntryKind) = items.forEachIndexed { i, item ->
            db.insert(
                "category",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    putNull("name")
                    put("kind", kind.name)
                    put("icon", item.icon)
                    put("seedKey", item.key)
                    put("archived", 0)
                    put("position", i)
                    put("createdAtMs", nowMs)
                },
            )
        }
        fun asset(items: List<SeedCategory>, group: AssetGroup) = items.forEachIndexed { i, item ->
            db.insert(
                "asset_category",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    putNull("name")
                    put("assetGroup", group.name)
                    put("icon", item.icon)
                    put("seedKey", item.key)
                    put("archived", 0)
                    put("position", i)
                    put("createdAtMs", nowMs)
                },
            )
        }
        cash(income, EntryKind.INCOME)
        cash(expense, EntryKind.EXPENSE)
        asset(investments, AssetGroup.INVESTMENT)
        asset(personal, AssetGroup.PERSONAL)
    }
}
