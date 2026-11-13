package io.github.luannamorim.myfinance.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations. Every schema change after version 1 adds one here, with a case in MigrationTest built
 * from the exported schema JSON in app/schemas. There is no destructive fallback: user data is never dropped.
 */
object Migrations {

    /**
     * 1 → 2 (app 1.1.0, transfers): `categoryId` becomes nullable and `assetId` is added on templates and
     * occurrences; `occurrenceId` links asset flows to the transfer they came from. SQLite can't change a
     * column's nullability, so the three tables are rebuilt (new table, copy, drop, rename, indices), as Room's
     * own auto-migrations do. Foreign keys are off while migrating, so dropping a parent cascades nothing;
     * Room checks them on the rebuilt tables afterwards. Also adds the predefined categories
     * "Loans Receivable" (personal assets), "Credit Card" and "Loans" (expenses).
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            rebuild(
                db,
                table = "transaction_template",
                create = "CREATE TABLE IF NOT EXISTS `_new_transaction_template` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `categoryId` INTEGER, `kind` TEXT NOT NULL, `currency` TEXT NOT NULL, " +
                    "`expectedAmount` INTEGER NOT NULL, `recurrenceUnit` TEXT NOT NULL, `recurrenceInterval` INTEGER NOT NULL, " +
                    "`startDate` INTEGER NOT NULL, `anchorDate` INTEGER NOT NULL, `endDate` INTEGER, `description` TEXT, " +
                    "`active` INTEGER NOT NULL, `generatedThrough` INTEGER, `createdAtMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL, " +
                    "`assetId` INTEGER, " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                    "FOREIGN KEY(`assetId`) REFERENCES `asset`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
                columns = "`id`, `name`, `categoryId`, `kind`, `currency`, `expectedAmount`, `recurrenceUnit`, `recurrenceInterval`, " +
                    "`startDate`, `anchorDate`, `endDate`, `description`, `active`, `generatedThrough`, `createdAtMs`, `updatedAtMs`",
                indices = listOf(
                    "CREATE INDEX IF NOT EXISTS `index_transaction_template_categoryId` ON `transaction_template` (`categoryId`)",
                    "CREATE INDEX IF NOT EXISTS `index_transaction_template_assetId` ON `transaction_template` (`assetId`)",
                ),
            )
            rebuild(
                db,
                table = "transaction_occurrence",
                create = "CREATE TABLE IF NOT EXISTS `_new_transaction_occurrence` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`templateId` INTEGER, `scheduledDate` INTEGER, `dueDate` INTEGER NOT NULL, `name` TEXT NOT NULL, `categoryId` INTEGER, " +
                    "`kind` TEXT NOT NULL, `currency` TEXT NOT NULL, `expectedAmount` INTEGER NOT NULL, `description` TEXT, " +
                    "`state` TEXT NOT NULL, `actualAmount` INTEGER, `actualCurrency` TEXT, `actualDate` INTEGER, `note` TEXT, " +
                    "`confirmedAtMs` INTEGER, `customized` INTEGER NOT NULL, `createdAtMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL, " +
                    "`assetId` INTEGER, " +
                    "FOREIGN KEY(`templateId`) REFERENCES `transaction_template`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                    "FOREIGN KEY(`assetId`) REFERENCES `asset`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
                columns = "`id`, `templateId`, `scheduledDate`, `dueDate`, `name`, `categoryId`, `kind`, `currency`, `expectedAmount`, " +
                    "`description`, `state`, `actualAmount`, `actualCurrency`, `actualDate`, `note`, `confirmedAtMs`, `customized`, " +
                    "`createdAtMs`, `updatedAtMs`",
                indices = listOf(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_transaction_occurrence_templateId_scheduledDate` ON `transaction_occurrence` (`templateId`, `scheduledDate`)",
                    "CREATE INDEX IF NOT EXISTS `index_transaction_occurrence_dueDate` ON `transaction_occurrence` (`dueDate`)",
                    "CREATE INDEX IF NOT EXISTS `index_transaction_occurrence_actualDate` ON `transaction_occurrence` (`actualDate`)",
                    "CREATE INDEX IF NOT EXISTS `index_transaction_occurrence_state` ON `transaction_occurrence` (`state`)",
                    "CREATE INDEX IF NOT EXISTS `index_transaction_occurrence_categoryId` ON `transaction_occurrence` (`categoryId`)",
                    "CREATE INDEX IF NOT EXISTS `index_transaction_occurrence_assetId` ON `transaction_occurrence` (`assetId`)",
                ),
            )
            rebuild(
                db,
                table = "asset_flow",
                create = "CREATE TABLE IF NOT EXISTS `_new_asset_flow` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`assetId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `date` INTEGER NOT NULL, `amount` INTEGER NOT NULL, " +
                    "`quantityDelta` TEXT, `note` TEXT, `createdAtMs` INTEGER NOT NULL, `occurrenceId` INTEGER, " +
                    "FOREIGN KEY(`assetId`) REFERENCES `asset`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`occurrenceId`) REFERENCES `transaction_occurrence`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
                columns = "`id`, `assetId`, `kind`, `date`, `amount`, `quantityDelta`, `note`, `createdAtMs`",
                indices = listOf(
                    "CREATE INDEX IF NOT EXISTS `index_asset_flow_assetId_date` ON `asset_flow` (`assetId`, `date`)",
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_asset_flow_occurrenceId` ON `asset_flow` (`occurrenceId`)",
                ),
            )
            // New predefined categories, before "Other …" (as in CategorySeed for new installs).
            val now = System.currentTimeMillis()
            db.execSQL("UPDATE `asset_category` SET `position` = `position` + 1 WHERE `seedKey` = 'asset.other_assets'")
            db.execSQL(
                "INSERT OR IGNORE INTO `asset_category` (`name`, `assetGroup`, `icon`, `seedKey`, `archived`, `position`, `createdAtMs`) " +
                    "SELECT NULL, 'PERSONAL', 'handshake', 'asset.loans_receivable', 0, " +
                    "COALESCE((SELECT `position` FROM `asset_category` WHERE `seedKey` = 'asset.other_assets') - 1, 4), $now",
            )
            db.execSQL("UPDATE `category` SET `position` = `position` + 2 WHERE `seedKey` = 'expense.other_expenses'")
            listOf("expense.credit_card" to "credit_card", "expense.loans" to "account_balance").forEachIndexed { i, (key, icon) ->
                db.execSQL(
                    "INSERT OR IGNORE INTO `category` (`name`, `kind`, `icon`, `seedKey`, `archived`, `position`, `createdAtMs`) " +
                        "SELECT NULL, 'EXPENSE', '$icon', '$key', 0, " +
                        "COALESCE((SELECT `position` FROM `category` WHERE `seedKey` = 'expense.other_expenses') - ${2 - i}, ${14 + i}), $now",
                )
            }
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)

    /** Copies [columns] (those that exist before and after) into the rebuilt [table]; new columns start NULL. */
    private fun rebuild(db: SupportSQLiteDatabase, table: String, create: String, columns: String, indices: List<String>) {
        db.execSQL(create)
        db.execSQL("INSERT INTO `_new_$table` ($columns) SELECT $columns FROM `$table`")
        db.execSQL("DROP TABLE `$table`")
        db.execSQL("ALTER TABLE `_new_$table` RENAME TO `$table`")
        indices.forEach(db::execSQL)
    }
}
