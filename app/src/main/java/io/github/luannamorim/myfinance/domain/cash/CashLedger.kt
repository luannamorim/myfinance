package io.github.luannamorim.myfinance.domain.cash

import io.github.luannamorim.myfinance.domain.model.CashAdjustment
import io.github.luannamorim.myfinance.domain.model.Occurrence
import io.github.luannamorim.myfinance.domain.model.TransactionTemplate

/** Everything the cash calculations need, read in one snapshot. */
data class CashLedger(
    val templates: List<TransactionTemplate> = emptyList(),
    val occurrences: List<Occurrence> = emptyList(),
    val adjustments: List<CashAdjustment> = emptyList(),
)
