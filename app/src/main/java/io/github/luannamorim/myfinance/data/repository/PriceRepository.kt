package io.github.luannamorim.myfinance.data.repository

import androidx.room.withTransaction
import io.github.luannamorim.myfinance.core.time.TimeSource
import io.github.luannamorim.myfinance.data.db.MyFinanceDatabase
import io.github.luannamorim.myfinance.data.db.entity.AssetPriceEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetSplitEntity
import io.github.luannamorim.myfinance.data.market.QuoteClient
import io.github.luannamorim.myfinance.data.prefs.PriceError
import io.github.luannamorim.myfinance.data.prefs.PriceState
import io.github.luannamorim.myfinance.data.prefs.PriceStateStore
import io.github.luannamorim.myfinance.data.prefs.SettingsRepository
import io.github.luannamorim.myfinance.domain.market.ChartResponse
import io.github.luannamorim.myfinance.domain.market.ChartResult
import io.github.luannamorim.myfinance.domain.market.PricePoint
import io.github.luannamorim.myfinance.domain.market.QuoteFormatException
import io.github.luannamorim.myfinance.domain.market.Split
import io.github.luannamorim.myfinance.domain.market.TickerMatch
import io.github.luannamorim.myfinance.domain.market.YahooChartParser
import io.github.luannamorim.myfinance.domain.market.YahooSearchParser
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.PriceSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PriceRefreshResult {
    data class Updated(val newRows: Int) : PriceRefreshResult
    data object UpToDate : PriceRefreshResult
    data object Disabled : PriceRefreshResult
    data object Throttled : PriceRefreshResult
    data object NothingTracked : PriceRefreshResult
    data class Failed(val reason: PriceError) : PriceRefreshResult
}

/** What a ticker looks like before saving a tracked asset: its last close and the splits after [from]. */
data class QuotePreview(
    val symbol: String,
    val name: String?,
    val price: PricePoint,
    val splitsSince: List<Split>,
    val firstTradeDate: LocalDate?,
)

class TickerNotFoundException : IllegalArgumentException("Ticker not found")

/**
 * Daily closes and splits of tracked assets (`asset.ticker`), fetched from Yahoo Finance and stored raw (not
 * split-adjusted). Fetched only on foreground or on an explicit request; never in the background.
 */
@Singleton
class PriceRepository @Inject constructor(
    private val db: MyFinanceDatabase,
    private val client: QuoteClient,
    private val store: PriceStateStore,
    private val settings: SettingsRepository,
    private val time: TimeSource,
    private val backup: BackupTrigger,
) {
    private val dao = db.marketDao()
    private val portfolio = db.portfolioDao()
    private val mutex = Mutex()

    val state: Flow<PriceState> = store.state

    /**
     * Automatic runs ([force] false) respect the "Fetch stock quotes" switch and a 15-minute throttle counted from
     * the last attempt; "Update now" and creating a tracked asset ([force] true) ignore both. No request at all is
     * made when no asset has a ticker. A network error stops the run; an unknown symbol or a bad response only
     * skips that symbol.
     */
    suspend fun refresh(force: Boolean = false): PriceRefreshResult = mutex.withLock {
        if (!force && !settings.current().autoFetchPrices) return PriceRefreshResult.Disabled
        val symbols = trackedSymbols()
        if (symbols.isEmpty()) return PriceRefreshResult.NothingTracked
        val now = time.nowMs()
        val lastAttempt = store.current().lastAttemptMs
        if (!force && lastAttempt != null && now - lastAttempt < THROTTLE_MS) return PriceRefreshResult.Throttled

        var newRows = 0
        var fetched = false
        var firstError: PriceError? = null
        for ((symbol, tracked) in symbols.toSortedMap()) {
            val lastPrice = dao.lastPriceDate(symbol)?.let(LocalDate::ofEpochDay)
            if (tracked.allClosed && lastPrice != null && tracked.lastDisposal != null && lastPrice >= tracked.lastDisposal) continue
            val fullFrom = tracked.earliest.minusDays(MARGIN_DAYS)
            val from = lastPrice?.minusDays(MARGIN_DAYS) ?: fullFrom
            try {
                var result = fetch(symbol, from, now)
                if (result == null) {
                    firstError = firstError ?: PriceError.NOT_FOUND
                    continue
                }
                val storedSplits = dao.splitsOf(symbol).map { it.date }.toSet()
                if (from != fullFrom && result.splits.any { it.date.toEpochDay() !in storedSplits }) {
                    result = fetch(symbol, fullFrom, now)
                    if (result == null) {
                        firstError = firstError ?: PriceError.NOT_FOUND
                        continue
                    }
                }
                newRows += save(symbol, result, now)
                fetched = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: QuoteFormatException) {
                firstError = firstError ?: PriceError.FORMAT
            } catch (_: IOException) {
                finish(now, newRows, fetched, PriceError.NETWORK)
                return PriceRefreshResult.Failed(PriceError.NETWORK)
            }
        }
        finish(now, newRows, fetched, firstError)
        // A failed symbol is reported even when others brought new rows (those are stored and backed up anyway).
        when {
            firstError != null -> PriceRefreshResult.Failed(firstError)
            newRows > 0 -> PriceRefreshResult.Updated(newRows)
            else -> PriceRefreshResult.UpToDate
        }
    }

    /** Ticker search; [IOException] and [QuoteFormatException] propagate. */
    suspend fun search(query: String): List<TickerMatch> = YahooSearchParser.parse(client.search(query))

    /**
     * The last close of [symbol] and its splits after [from], fetched from [from] to now; nothing is stored.
     * Throws [TickerNotFoundException] for an unknown symbol; [IOException] and [QuoteFormatException] propagate.
     */
    suspend fun preview(symbol: String, from: LocalDate): QuotePreview {
        val result = fetch(symbol, from, time.nowMs()) ?: throw TickerNotFoundException()
        val last = result.prices.lastOrNull() ?: throw QuoteFormatException("No prices")
        return QuotePreview(result.symbol, result.longName, last, result.splits.filter { it.date > from }, result.firstTradeDate)
    }

    private suspend fun fetch(symbol: String, from: LocalDate, nowMs: Long): ChartResult? =
        when (val response = YahooChartParser.parse(client.chart(symbol, from, nowMs / 1000))) {
            is ChartResponse.Found -> response.result
            ChartResponse.NotFound -> null
        }

    /** Stores [result] for [symbol]; returns the new rows (price days not stored before plus splits inserted). */
    private suspend fun save(symbol: String, result: ChartResult, nowMs: Long): Int = db.withTransaction {
        val known = dao.priceDates(symbol).toSet()
        val newPrices = result.prices.map { it.date.toEpochDay() }.distinct().count { it !in known }
        val insertedSplits = dao.insertSplits(
            result.splits.map {
                AssetSplitEntity(
                    symbol = symbol, date = it.date.toEpochDay(),
                    numerator = it.numerator.stripTrailingZeros().toPlainString(),
                    denominator = it.denominator.stripTrailingZeros().toPlainString(),
                )
            },
        ).count { it != -1L }
        dao.upsertPrices(
            result.prices.map {
                AssetPriceEntity(
                    symbol = symbol, date = it.date.toEpochDay(), price = it.price.stripTrailingZeros().toPlainString(),
                    source = PriceSource.YAHOO, fetchedAtMs = nowMs,
                )
            },
        )
        newPrices + insertedSplits
    }

    private suspend fun finish(nowMs: Long, newRows: Int, fetched: Boolean, error: PriceError?) {
        if (newRows > 0) backup.onDataChanged()
        store.record(nowMs, fetched, error)
    }

    private class Tracked(val earliest: LocalDate, val allClosed: Boolean, val lastDisposal: LocalDate?)

    private suspend fun trackedSymbols(): Map<String, Tracked> {
        val assets = portfolio.listAssets().filter { it.ticker != null }
        if (assets.isEmpty()) return emptyMap()
        val flows = portfolio.listFlows().groupBy { it.assetId }
        return assets.groupBy { it.ticker!! }.mapValues { (_, list) ->
            val assetFlows = list.map { flows[it.id].orEmpty() }
            val earliest = list.zip(assetFlows).minOf { (asset, f) -> f.minOfOrNull { it.date } ?: asset.acquisitionDate }
            val disposals = assetFlows.map { f -> f.filter { it.kind == FlowKind.FULL_DISPOSAL }.maxOfOrNull { it.date } }
            Tracked(
                earliest = LocalDate.ofEpochDay(earliest),
                allClosed = disposals.all { it != null },
                lastDisposal = disposals.filterNotNull().maxOrNull()?.let(LocalDate::ofEpochDay),
            )
        }
    }

    companion object {
        const val THROTTLE_MS = 15 * 60 * 1000L

        /** Days fetched before the earliest flow or the last stored close, so a date always has a close on or before it. */
        const val MARGIN_DAYS = 7L
    }
}
