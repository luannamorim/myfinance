package io.github.luannamorim.myfinance.data.repository

import io.github.luannamorim.myfinance.data.db.MyFinanceDatabase
import io.github.luannamorim.myfinance.data.db.entity.AssetFlowEntity
import io.github.luannamorim.myfinance.data.db.entity.OccurrenceEntity
import io.github.luannamorim.myfinance.domain.model.EntryKind
import io.github.luannamorim.myfinance.domain.model.FlowKind
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.OccurrenceState

/** A transfer can't be dated before the asset's initial investment. */
class TransferBeforeAcquisitionException : IllegalArgumentException("The transfer is dated before the asset was acquired")

/** A transfer between currencies needs the amount the asset received or gave, in the asset's currency. */
class TransferAmountMissingException : IllegalArgumentException("The amount in the asset currency is required")

/** The transfer created the asset's initial investment: delete it, or the asset, instead of undoing it. */
class InitialTransferException : IllegalStateException("This transfer is the asset's initial investment")

/**
 * Keeps a transfer and the asset flow it created in step (docs/ARCHITECTURE.md, "Transfers"): a confirmed
 * TRANSFER_OUT has a CONTRIBUTION, a confirmed TRANSFER_IN a WITHDRAWAL (or the disposal it was recorded with),
 * in the asset's currency; any other state has none. Every function must run inside a database transaction.
 */
internal class TransferFlows(db: MyFinanceDatabase) {
    private val portfolio = db.portfolioDao()

    /**
     * Brings the flow of [occurrence] (as just written) up to date. [assetAmount] is what the asset received or
     * gave when the cash currency differs from the asset's; without it an existing flow keeps its amount.
     */
    suspend fun sync(occurrence: OccurrenceEntity, assetAmount: Money?, nowMs: Long) {
        val existing = portfolio.getFlowByOccurrence(occurrence.id)
        if (!occurrence.kind.isTransfer || occurrence.state != OccurrenceState.CONFIRMED) {
            if (existing != null) unlink(existing)
            return
        }
        val assetId = checkNotNull(occurrence.assetId) { "A transfer needs an asset" }
        val asset = checkNotNull(portfolio.getAsset(assetId)) { "Asset $assetId not found" }
        val actual = Money(checkNotNull(occurrence.actualAmount), occurrence.actualCurrency ?: occurrence.currency)
        val date = checkNotNull(occurrence.actualDate)
        val amount = when {
            actual.currency == asset.currency -> actual.minor
            assetAmount != null -> {
                require(assetAmount.currency == asset.currency && assetAmount.minor > 0) { "Invalid amount in the asset currency" }
                assetAmount.minor
            }
            existing != null && existing.assetId == assetId -> existing.amount
            else -> throw TransferAmountMissingException()
        }
        val flows = portfolio.getFlows(assetId)
        if (existing?.kind != FlowKind.INITIAL) {
            val initial = flows.firstOrNull { it.kind == FlowKind.INITIAL }
            if (initial != null && date < initial.date) throw TransferBeforeAcquisitionException()
        }
        if (existing != null && existing.assetId == assetId) {
            portfolio.updateFlow(existing.copy(date = if (existing.kind == FlowKind.INITIAL) existing.date else date, amount = amount, note = occurrence.note))
            return
        }
        if (flows.any { it.kind == FlowKind.FULL_DISPOSAL }) throw AssetClosedException()
        if (existing != null) unlink(existing)
        portfolio.insertFlow(
            AssetFlowEntity(
                assetId = assetId,
                kind = if (occurrence.kind == EntryKind.TRANSFER_OUT) FlowKind.CONTRIBUTION else FlowKind.WITHDRAWAL,
                date = date, amount = amount, note = occurrence.note, createdAtMs = nowMs, occurrenceId = occurrence.id,
            ),
        )
    }

    /** Throws [InitialTransferException] when the transfer of [occurrenceId] created an initial investment. */
    suspend fun checkNotInitial(occurrenceId: Long) {
        if (portfolio.getFlowByOccurrence(occurrenceId)?.kind == FlowKind.INITIAL) throw InitialTransferException()
    }

    /** Before deleting the transfer [occurrenceId]: removes its flow (an initial investment stays, unlinked). */
    suspend fun detach(occurrenceId: Long) {
        portfolio.getFlowByOccurrence(occurrenceId)?.let { unlink(it) }
    }

    private suspend fun unlink(flow: AssetFlowEntity) {
        if (flow.kind == FlowKind.INITIAL) portfolio.updateFlow(flow.copy(occurrenceId = null)) else portfolio.deleteFlow(flow.id)
    }
}
