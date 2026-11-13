package io.github.luannamorim.myfinance.domain.model

import androidx.annotation.Keep

/**
 * What a transaction does to cash. Categories are INCOME or EXPENSE only (fixed once created). Transfers move
 * money between cash and an asset and are neither income nor expense: TRANSFER_OUT takes cash into an asset (a
 * contribution), TRANSFER_IN brings it back from one (a withdrawal). Kept by R8: routes serialize it by name.
 */
@Keep
enum class EntryKind(val cashInflow: Boolean, val isTransfer: Boolean) {
    INCOME(cashInflow = true, isTransfer = false),
    EXPENSE(cashInflow = false, isTransfer = false),
    TRANSFER_OUT(cashInflow = false, isTransfer = true),
    TRANSFER_IN(cashInflow = true, isTransfer = true),
}

/** Unit of a recurrence rule. */
enum class RecurrenceUnit { DAY, WEEK, MONTH, YEAR }

/** Stored state of an occurrence; the displayed status (Overdue, Pending…) is derived from it and the date. */
enum class OccurrenceState { PENDING, CONFIRMED, CANCELLED }

/** OPENING is the starting balance of a currency (at most one); ADJUSTMENT aligns the balance with reality. */
enum class AdjustmentKind { OPENING, ADJUSTMENT }

/** Investments vs. personal assets (business, equity, real estate, vehicles…). */
enum class AssetGroup { INVESTMENT, PERSONAL }

/** Asset cash flows. Amounts are always positive; [inflow] says whether money goes into the asset. Kept for routes. */
@Keep
enum class FlowKind(val inflow: Boolean) {
    INITIAL(true),
    CONTRIBUTION(true),
    WITHDRAWAL(false),
    PARTIAL_DISPOSAL(false),
    FULL_DISPOSAL(false),
}

/** Where an exchange rate came from. A MANUAL rate wins over a BCB rate on the same date. */
enum class RateSource { BCB, MANUAL }
