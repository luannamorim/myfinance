package io.github.luannamorim.myfinance.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.luannamorim.myfinance.R
import io.github.luannamorim.myfinance.core.format.AmountInput
import io.github.luannamorim.myfinance.data.repository.AssetClosedException
import io.github.luannamorim.myfinance.data.repository.InitialTransferException
import io.github.luannamorim.myfinance.data.repository.TransferAmountMissingException
import io.github.luannamorim.myfinance.data.repository.TransferBeforeAcquisitionException
import io.github.luannamorim.myfinance.data.repository.TransferLockedException
import io.github.luannamorim.myfinance.domain.fx.Converter
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import io.github.luannamorim.myfinance.domain.model.Asset
import io.github.luannamorim.myfinance.domain.model.AssetFlow
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.portfolio.PortfolioData
import io.github.luannamorim.myfinance.ui.components.ConfirmDialog
import io.github.luannamorim.myfinance.ui.components.CurrencyInput
import io.github.luannamorim.myfinance.ui.components.FinanceChip
import io.github.luannamorim.myfinance.ui.components.SwitchRow
import io.github.luannamorim.myfinance.ui.theme.Spacing
import java.time.LocalDate

/** Icon of transfers between cash and the portfolio (they have no category). */
val TransferIcon: ImageVector get() = Icons.Rounded.SwapHoriz

/** The asset of a transfer, as the confirm sheet and editor need it. */
data class TransferTarget(val assetId: Long, val name: String, val currency: Currency, val acquired: LocalDate, val closed: Boolean)

fun PortfolioData.transferTarget(assetId: Long?): TransferTarget? {
    val asset: Asset = assets.firstOrNull { it.id == assetId } ?: return null
    val flows: List<AssetFlow> = this.flows[asset.id].orEmpty()
    return TransferTarget(
        assetId = asset.id,
        name = asset.name,
        currency = asset.currency,
        acquired = flows.firstOrNull { it.kind == FlowKind.INITIAL }?.date ?: asset.acquisitionDate,
        closed = flows.any { it.kind == FlowKind.FULL_DISPOSAL },
    )
}

/** Assets a new transfer can go to or come from: open ones, investments first, by name. */
fun PortfolioData.transferTargets(): List<TransferTarget> {
    val group = categories.associate { it.id to it.group }
    return assets
        .sortedWith(compareBy({ group[it.categoryId]?.ordinal ?: Int.MAX_VALUE }, { it.name.lowercase() }))
        .mapNotNull { transferTarget(it.id) }
        .filterNot { it.closed }
}

/** Prefills the amount on the asset side of a transfer between currencies, at the rate of [on]. */
fun ExchangeRates.suggestAssetAmount(cash: Money, assetCurrency: Currency, on: LocalDate): Money? =
    if (cash.currency == assetCurrency) cash else Converter(this).convert(cash, assetCurrency, on)

@Composable
fun EntryKind.directionLabel(): String = stringResource(
    when (this) {
        EntryKind.TRANSFER_OUT -> R.string.transfer_to_asset
        EntryKind.TRANSFER_IN -> R.string.transfer_from_asset
        EntryKind.INCOME -> R.string.kind_income
        EntryKind.EXPENSE -> R.string.kind_expense
    },
)

/** Why a write was refused, in words the user can act on. */
enum class ActionError { ASSET_CLOSED, BEFORE_ACQUISITION, AMOUNT_MISSING, INITIAL_TRANSFER, TRANSFER_LOCKED, FAILED }

fun Throwable.toActionError(): ActionError = when (this) {
    is AssetClosedException -> ActionError.ASSET_CLOSED
    is TransferBeforeAcquisitionException -> ActionError.BEFORE_ACQUISITION
    is TransferAmountMissingException -> ActionError.AMOUNT_MISSING
    is InitialTransferException -> ActionError.INITIAL_TRANSFER
    is TransferLockedException -> ActionError.TRANSFER_LOCKED
    else -> ActionError.FAILED
}

@Composable
fun ActionErrorDialog(error: ActionError?, onDismiss: () -> Unit) {
    if (error == null) return
    ConfirmDialog(
        title = stringResource(R.string.error_title),
        message = stringResource(
            when (error) {
                ActionError.ASSET_CLOSED -> R.string.error_asset_closed
                ActionError.BEFORE_ACQUISITION -> R.string.error_before_acquisition
                ActionError.AMOUNT_MISSING -> R.string.error_amount_missing
                ActionError.INITIAL_TRANSFER -> R.string.error_initial_transfer
                ActionError.TRANSFER_LOCKED -> R.string.error_transfer_locked
                ActionError.FAILED -> R.string.error_failed
            },
        ),
        confirmText = stringResource(R.string.action_ok),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        dismissText = null,
    )
}

/**
 * The cash side of a contribution or withdrawal recorded on an asset: off by default; when on, the money
 * comes from cash ([inflow], a contribution) or goes to it, in [cashCurrency]. When that differs from the
 * asset's currency, [cashDigits] is the amount in cash, prefilled from [assetAmount] at the rate of [date].
 */
@Composable
fun CashLinkFields(
    linked: Boolean,
    onLinked: (Boolean) -> Unit,
    inflow: Boolean,
    assetAmount: Money?,
    date: LocalDate,
    cashCurrency: Currency,
    onCashCurrency: (Currency) -> Unit,
    cashDigits: String?,
    onCashDigits: (String) -> Unit,
    rates: ExchangeRates,
) {
    SwitchRow(
        stringResource(if (inflow) R.string.cash_link_from else R.string.cash_link_to),
        stringResource(if (inflow) R.string.cash_link_from_hint else R.string.cash_link_to_hint),
        linked,
        onLinked,
    )
    if (!linked || assetAmount == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Currency.entries.forEach { currency ->
            FinanceChip(currency.symbol, selected = currency == cashCurrency, onClick = { onCashCurrency(currency) })
        }
    }
    if (cashCurrency != assetAmount.currency) {
        CurrencyInput(
            digits = cashDigits ?: suggestedCashDigits(assetAmount, cashCurrency, date, rates),
            currency = cashCurrency,
            onDigits = onCashDigits,
            label = stringResource(if (inflow) R.string.cash_link_amount_out else R.string.cash_link_amount_in, cashCurrency.symbol),
            supportingText = stringResource(R.string.transfer_asset_amount_hint),
        )
    }
}

private fun suggestedCashDigits(assetAmount: Money, cashCurrency: Currency, date: LocalDate, rates: ExchangeRates): String =
    rates.suggestAssetAmount(assetAmount, cashCurrency, date)?.let { AmountInput.digitsOf(it.minor) }.orEmpty()

/** What left or entered cash for [assetAmount], or null when the flow isn't linked to cash (or the amount is missing). */
fun cashLinkAmount(linked: Boolean, assetAmount: Money, date: LocalDate, cashCurrency: Currency, cashDigits: String?, rates: ExchangeRates): Money? {
    if (!linked) return null
    if (cashCurrency == assetAmount.currency) return assetAmount
    val digits = cashDigits ?: suggestedCashDigits(assetAmount, cashCurrency, date, rates)
    val minor = AmountInput.minorOf(digits) ?: return null
    return if (minor > 0) Money(minor, cashCurrency) else null
}
