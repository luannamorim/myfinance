package io.github.luannamorim.myfinance.data.repository

import io.github.luannamorim.myfinance.data.db.MyFinanceDatabase
import io.github.luannamorim.myfinance.domain.fx.ExchangeRates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Stored USD→BRL rates (fetched from the Banco Central or typed by the user). */
@Singleton
class ExchangeRateRepository @Inject constructor(
    db: MyFinanceDatabase,
) {
    private val dao = db.rateDao()

    val rates: Flow<ExchangeRates> = dao.observeAll().map { list -> ExchangeRates(list.map { it.toDomain() }) }
}
