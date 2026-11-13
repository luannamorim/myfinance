package io.github.luannamorim.myfinance.domain.cash

import io.github.luannamorim.myfinance.domain.model.RecurrenceUnit

/** "Every [interval] [unit]s". Presets cover the common cases; anything else is a custom rule. */
data class RecurrenceRule(val unit: RecurrenceUnit, val interval: Int) {

    init {
        require(interval in 1..MAX_INTERVAL) { "Interval must be in 1..$MAX_INTERVAL" }
    }

    /** The preset this rule equals, or null for a custom rule. */
    fun presetOrNull(): Preset? = Preset.entries.firstOrNull { it.rule == this }

    enum class Preset(val rule: RecurrenceRule) {
        DAILY(RecurrenceRule(RecurrenceUnit.DAY, 1)),
        WEEKLY(RecurrenceRule(RecurrenceUnit.WEEK, 1)),
        BIWEEKLY(RecurrenceRule(RecurrenceUnit.WEEK, 2)),
        MONTHLY(RecurrenceRule(RecurrenceUnit.MONTH, 1)),
        BIMONTHLY(RecurrenceRule(RecurrenceUnit.MONTH, 2)),
        QUARTERLY(RecurrenceRule(RecurrenceUnit.MONTH, 3)),
        SEMIANNUAL(RecurrenceRule(RecurrenceUnit.MONTH, 6)),
        ANNUAL(RecurrenceRule(RecurrenceUnit.YEAR, 1)),
    }

    companion object {
        const val MAX_INTERVAL = 99
        val DAILY get() = Preset.DAILY.rule
        val WEEKLY get() = Preset.WEEKLY.rule
        val BIWEEKLY get() = Preset.BIWEEKLY.rule
        val MONTHLY get() = Preset.MONTHLY.rule
        val BIMONTHLY get() = Preset.BIMONTHLY.rule
        val QUARTERLY get() = Preset.QUARTERLY.rule
        val SEMIANNUAL get() = Preset.SEMIANNUAL.rule
        val ANNUAL get() = Preset.ANNUAL.rule
    }
}
