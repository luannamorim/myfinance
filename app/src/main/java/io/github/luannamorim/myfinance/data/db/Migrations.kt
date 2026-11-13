package io.github.luannamorim.myfinance.data.db

import androidx.room.migration.Migration

/**
 * Schema migrations. Every schema change after version 1 adds one here, with a case in MigrationTest built
 * from the exported schema JSON in app/schemas. There is no destructive fallback: user data is never dropped.
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}
