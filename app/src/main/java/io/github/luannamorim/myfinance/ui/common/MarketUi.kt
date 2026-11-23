package io.github.luannamorim.myfinance.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.domain.market.Split
import io.github.luannamorim.myfinance.domain.market.Ticker
import io.github.luannamorim.myfinance.domain.market.TickerType
import java.math.BigDecimal

/** "10 shares" / "10 ações" for stocks and ETFs; "0,05 BTC" for crypto ([Ticker.unitCode]). */
@Composable
fun unitsLabel(quantity: BigDecimal, ticker: String): String {
    val number = LocalFormats.current.money.formatQuantity(quantity)
    val coin = Ticker.unitCode(ticker)
    return if (coin != null) "$number $coin" else stringResource(R.string.units_shares, number)
}

@Composable
fun TickerType.label(): String = stringResource(
    when (this) {
        TickerType.STOCK -> R.string.asset_ticker_stock
        TickerType.ETF -> R.string.asset_ticker_etf
        TickerType.CRYPTO -> R.string.asset_ticker_crypto
    },
)

/** The category icon of a ticker type (names from CategoryIcons). */
val TickerType.icon: String
    get() = when (this) {
        TickerType.STOCK -> "show_chart"
        TickerType.ETF -> "stacked_line_chart"
        TickerType.CRYPTO -> "currency_bitcoin"
    }

/** "10:1" for a 10-for-1 split. */
val Split.ratio: String
    get() = "${numerator.stripTrailingZeros().toPlainString()}:${denominator.stripTrailingZeros().toPlainString()}"
