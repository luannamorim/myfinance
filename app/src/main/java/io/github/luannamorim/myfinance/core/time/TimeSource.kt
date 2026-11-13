package io.github.luannamorim.myfinance.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Wall clock and time zone, injected so tests control "today". */
interface TimeSource {
    fun nowMs(): Long
    fun zone(): ZoneId
}

/** The local date now. The cash model has no time of day: due dates and "today" are local dates. */
fun TimeSource.today(): LocalDate = Instant.ofEpochMilli(nowMs()).atZone(zone()).toLocalDate()

@Singleton
class SystemTimeSource @Inject constructor() : TimeSource {
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
