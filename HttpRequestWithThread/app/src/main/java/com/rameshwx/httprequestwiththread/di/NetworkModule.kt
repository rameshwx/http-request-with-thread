package com.rameshwx.httprequestwiththread.di

import com.rameshwx.httprequestwiththread.data.TrainingDataApi
import com.rameshwx.httprequestwiththread.data.image.AndroidProductImageDecoder
import com.rameshwx.httprequestwiththread.data.image.ProductImageDecoder
import com.rameshwx.httprequestwiththread.data.network.RawHttpTransport
import com.rameshwx.httprequestwiththread.data.network.RawHttpsClient
import com.rameshwx.httprequestwiththread.data.repository.GroceryRepository
import com.rameshwx.httprequestwiththread.data.repository.TrainingDataRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindings {
    @Binds
    @Singleton
    abstract fun bindGroceryRepository(implementation: TrainingDataRepository): GroceryRepository
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideRawHttpsClient(): RawHttpsClient = RawHttpsClient()

    @Provides
    @Singleton
    fun provideRawHttpTransport(client: RawHttpsClient): RawHttpTransport = client

    @Provides
    @Singleton
    fun provideTrainingDataApi(client: RawHttpTransport): TrainingDataApi = TrainingDataApi(client)

    @Provides
    @Singleton
    fun provideProductImageDecoder(): ProductImageDecoder = AndroidProductImageDecoder()
}
