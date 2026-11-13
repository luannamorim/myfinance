package io.github.luannamorim.myfinance.data.repository

import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.core.time.today
import io.github.luannamorim.myfinance.data.db.MyFinanceDatabase
import io.github.luannamorim.myfinance.data.db.entity.ExchangeRateEntity
import io.github.luannamorim.myfinance.data.prefs.RateError
import io.github.luannamorim.myfinance.data.prefs.RateState
import io.github.luannamorim.myfinance.data.prefs.RateStateStore
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.data.rates.RateClient
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.fx.PtaxParser
import io.github.luannamorim.myfinance.domain.fx.RateFormatException
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.RateSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RefreshResult {
    data class Updated(val count: Int) : RefreshResult
    data object UpToDate : RefreshResult
    data object Disabled : RefreshResult
    data object Throttled : RefreshResult
    data class Failed(val reason: RateError) : RefreshResult
}

/**
 * USD→BRL rates: PTAX fetched from the Banco Central (docs/ARCHITECTURE.md, "Exchange rates") and manual rates
 * typed by the user. A manual rate never gets overwritten; failures never remove known rates.
 */
@Singleton
class ExchangeRateRepository @Inject constructor(
    db: MyFinanceDatabase,
    private val client: RateClient,
    private val store: RateStateStore,
    private val settings: SettingsRepository,
    private val time: TimeSource,
    private val backup: BackupTrigger,
) {
    private val dao = db.rateDao()
    private val mutex = Mutex()

    val rates: Flow<ExchangeRates> = dao.observeAll().map { list -> ExchangeRates(list.map { it.toDomain() }) }

    val state: Flow<RateState> = store.state

    /**
     * Automatic runs ([force] false) respect the "Fetch exchange rates" switch and a 6-hour throttle. "Update"
     * ([force] true) is an explicit user action and ignores both.
     */
    suspend fun refresh(force: Boolean = false): RefreshResult = mutex.withLock {
        if (!force && !settings.current().autoFetchRates) return RefreshResult.Disabled
        val now = time.nowMs()
        val last = store.current().lastFetchMs
        if (!force && last != null && now - last < THROTTLE_MS) return RefreshResult.Throttled
        val windows = windows(time.today())
        try {
            var inserted = 0
            windows.forEach { (from, to) ->
                val parsed = PtaxParser.parse(client.fetch(from, to))
                inserted += dao.insertIgnore(
                    parsed.map { (date, rate) ->
                        ExchangeRateEntity(
                            base = Currency.USD, quote = Currency.BRL, date = date.toEpochDay(),
                            rateMicros = rate.toMicros(), source = RateSource.BCB, fetchedAtMs = now,
                        )
                    },
                ).count { it > 0 }
            }
            store.recordSuccess(now)
            if (inserted > 0) {
                backup.onDataChanged()
                RefreshResult.Updated(inserted)
            } else {
                RefreshResult.UpToDate
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: RateFormatException) {
            store.recordFailure(now, RateError.FORMAT)
            RefreshResult.Failed(RateError.FORMAT)
        } catch (_: IOException) {
            store.recordFailure(now, RateError.NETWORK)
            RefreshResult.Failed(RateError.NETWORK)
        }
    }

    /** Date ranges still to fetch, each at most 10 years long. */
    internal suspend fun windows(today: LocalDate): List<Pair<LocalDate, LocalDate>> {
        val earliestRecord = dao.earliestRecordDay()?.let(LocalDate::ofEpochDay)
        // A week of margin so the record's date has a business day (with a PTAX) on or before it.
        val earliest = earliestRecord?.minusDays(BACKFILL_MARGIN_DAYS)
        val defaultStart = today.minusDays(DEFAULT_DAYS)
        val lastBcb = dao.latestBcbDate()?.let(LocalDate::ofEpochDay)
        val firstBcb = dao.firstBcbDate()?.let(LocalDate::ofEpochDay)
        val ranges = mutableListOf<Pair<LocalDate, LocalDate>>()
        if (lastBcb == null || firstBcb == null) {
            ranges += minOf(earliest ?: defaultStart, defaultStart) to today
        } else {
            if (earliestRecord != null && earliest != null && earliestRecord.isBefore(firstBcb)) ranges += earliest to firstBcb.minusDays(1)
            if (lastBcb.isBefore(today)) ranges += lastBcb.plusDays(1) to today
        }
        return ranges.flatMap { (from, to) -> split(from, to) }
    }

    suspend fun setManual(date: LocalDate, rate: BigDecimal) {
        require(rate > BigDecimal.ZERO) { "Rate must be positive" }
        dao.upsertManual(
            ExchangeRateEntity(
                base = Currency.USD, quote = Currency.BRL, date = date.toEpochDay(),
                rateMicros = rate.toMicros(), source = RateSource.MANUAL, fetchedAtMs = time.nowMs(),
            ),
        )
        backup.onDataChanged()
    }

    suspend fun deleteManual(date: LocalDate) {
        dao.deleteManual(date.toEpochDay())
        backup.onDataChanged()
    }

    private fun BigDecimal.toMicros(): Long = movePointRight(6).setScale(0, RoundingMode.HALF_EVEN).longValueExact()

    companion object {
        const val THROTTLE_MS = 6 * 60 * 60 * 1000L
        const val DEFAULT_DAYS = 30L
        const val BACKFILL_MARGIN_DAYS = 7L
        private const val MAX_WINDOW_YEARS = 10L

        internal fun split(from: LocalDate, to: LocalDate): List<Pair<LocalDate, LocalDate>> {
            if (from.isAfter(to)) return emptyList()
            val result = mutableListOf<Pair<LocalDate, LocalDate>>()
            var start = from
            while (!start.isAfter(to)) {
                val end = minOf(start.plusYears(MAX_WINDOW_YEARS).minusDays(1), to)
                result += start to end
                start = end.plusDays(1)
            }
            return result
        }
    }
}
