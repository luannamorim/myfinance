package io.github.luannamorim.myfinance.data.repository

import io.github.luannamorim.myfinance.data.db.entity.AssetCategoryEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetFlowEntity
import io.github.luannamorim.myfinance.data.db.entity.AssetValuationEntity
import io.github.luannamorim.myfinance.data.db.entity.CashAdjustmentEntity
import io.github.luannamorim.myfinance.data.db.entity.CategoryEntity
import io.github.luannamorim.myfinance.data.db.entity.ExchangeRateEntity
import io.github.luannamorim.myfinance.data.db.entity.OccurrenceEntity
import io.github.luannamorim.myfinance.data.db.entity.TransactionTemplateEntity
import io.github.luannamorim.myfinance.domain.cash.RecurrenceRule
import io.github.luannamorim.myfinance.domain.model.Asset
import io.github.luannamorim.myfinance.domain.model.AssetCategory
import io.github.luannamorim.myfinance.domain.model.AssetFlow
import io.github.luannamorim.myfinance.domain.model.CashAdjustment
import io.github.luannamorim.myfinance.domain.model.Category
import io.github.luannamorim.myfinance.domain.model.Currency
import io.github.luannamorim.myfinance.domain.model.ExchangeRate
import io.github.luannamorim.myfinance.domain.model.Money
import io.github.luannamorim.myfinance.domain.model.Occurrence
import io.github.luannamorim.myfinance.domain.model.TransactionTemplate
import io.github.luannamorim.myfinance.domain.model.Valuation
import java.math.BigDecimal
import java.time.LocalDate

internal fun Long.toDate(): LocalDate = LocalDate.ofEpochDay(this)

internal fun CategoryEntity.toDomain() = Category(id, name, kind, icon, seedKey, archived, position)

internal fun AssetCategoryEntity.toDomain() = AssetCategory(id, name, assetGroup, icon, seedKey, archived, position)

internal fun TransactionTemplateEntity.toDomain() = TransactionTemplate(
    id = id,
    name = name,
    categoryId = categoryId,
    kind = kind,
    expected = Money(expectedAmount, currency),
    rule = RecurrenceRule(recurrenceUnit, recurrenceInterval),
    startDate = startDate.toDate(),
    anchorDate = anchorDate.toDate(),
    endDate = endDate?.toDate(),
    description = description,
    active = active,
    generatedThrough = generatedThrough?.toDate(),
)

internal fun OccurrenceEntity.toDomain() = Occurrence(
    id = id,
    templateId = templateId,
    scheduledDate = scheduledDate?.toDate(),
    dueDate = dueDate.toDate(),
    name = name,
    categoryId = categoryId,
    kind = kind,
    expected = Money(expectedAmount, currency),
    description = description,
    state = state,
    actual = actualAmount?.let { Money(it, actualCurrency ?: currency) },
    actualDate = actualDate?.toDate(),
    note = note,
    customized = customized,
)

internal fun CashAdjustmentEntity.toDomain() = CashAdjustment(id, kind, date.toDate(), Money(amount, currency), note)

internal fun AssetEntity.toDomain() = Asset(
    id = id,
    name = name,
    categoryId = categoryId,
    currency = currency,
    description = description,
    institution = institution,
    acquisitionDate = acquisitionDate.toDate(),
    quantity = quantity?.let(::BigDecimal),
    notes = notes,
    imagePath = imagePath,
)

internal fun AssetFlowEntity.toDomain(currency: Currency) =
    AssetFlow(id, assetId, kind, date.toDate(), Money(amount, currency), quantityDelta?.let(::BigDecimal), note)

internal fun AssetValuationEntity.toDomain(currency: Currency) = Valuation(id, assetId, date.toDate(), Money(value, currency), note)

internal fun ExchangeRateEntity.toDomain() = ExchangeRate(date.toDate(), BigDecimal.valueOf(rateMicros, 6), source)
