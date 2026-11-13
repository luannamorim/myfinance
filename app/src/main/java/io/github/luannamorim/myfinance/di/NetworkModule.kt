package io.github.luannamorim.myfinance.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.luannamorim.myfinance.data.rates.BcbRateClient
import io.github.luannamorim.myfinance.data.rates.RateClient

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds
    abstract fun bindRateClient(impl: BcbRateClient): RateClient
}
