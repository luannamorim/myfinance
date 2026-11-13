package io.github.luannamorim.myfinance.core.time

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Controllable clock for tests; São Paulo by default. */
class FakeTimeSource(
    var nowMs: Long = at("2026-11-12", "10:00"),
    var zoneId: ZoneId = SAO_PAULO,
) : TimeSource {
    override fun nowMs(): Long = nowMs
    override fun zone(): ZoneId = zoneId

    /** Moves the clock to [date] at [time] local time in [zoneId]. */
    fun set(date: String, time: String = "10:00") {
        nowMs = at(date, time, zoneId)
    }

    companion object {
        val SAO_PAULO: ZoneId = ZoneId.of("America/Sao_Paulo")

        fun at(date: String, time: String, zone: ZoneId = SAO_PAULO): Long =
            LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()
    }
}
