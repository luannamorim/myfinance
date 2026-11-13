package io.github.luannamorim.myfinance.domain.model

import androidx.annotation.Keep

/** Income or expense. Fixed for a category once created. Kept by R8: navigation routes serialize it by name. */
@Keep
enum class EntryKind { INCOME, EXPENSE }

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
