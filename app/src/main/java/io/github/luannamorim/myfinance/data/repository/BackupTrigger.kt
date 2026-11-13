package io.github.luannamorim.myfinance.data.repository

/** Told about every data change so the next automatic backup knows there is something to save. */
fun interface BackupTrigger {
    fun onDataChanged()

    /** Cancels a pending change-triggered run (used before a restore replaces the data). */
    fun cancelPending() {}
}
