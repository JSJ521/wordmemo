package com.wordmemo.app.di

import android.content.Context
import com.wordmemo.app.data.encryption.ApiKeyCipher
import com.wordmemo.app.data.io.CsvImporter
import com.wordmemo.app.data.io.JsonExporter
import com.wordmemo.app.data.network.AiApiClient
import com.wordmemo.app.data.network.AiApiRequestBuilder
import com.wordmemo.app.data.network.AiApiResponseParser
import com.wordmemo.app.data.network.NetworkMonitor
import com.wordmemo.app.domain.fsrs.FSRSAlgorithm
import com.wordmemo.app.domain.fsrs.FSRSDefaults
import com.wordmemo.app.domain.fsrs.FSRSOptimizer
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideFSRSAlgorithm(): FSRSAlgorithm = FSRSAlgorithm()

    @Provides
    @Singleton
    fun provideFSRSOptimizer(): FSRSOptimizer = FSRSOptimizer()

    @Provides
    @Singleton
    fun provideNetworkMonitor(@ApplicationContext ctx: Context): NetworkMonitor =
        NetworkMonitor(ctx)

    @Provides
    @Singleton
    fun provideApiKeyCipher(): ApiKeyCipher = ApiKeyCipher()

    @Provides
    @Singleton
    fun provideAiApiClient(gson: Gson): AiApiClient = AiApiClient(gson)

    @Provides
    @Singleton
    fun provideAiApiRequestBuilder(): AiApiRequestBuilder = AiApiRequestBuilder()

    @Provides
    @Singleton
    fun provideAiApiResponseParser(gson: Gson): AiApiResponseParser = AiApiResponseParser(gson)

    @Provides
    @Singleton
    fun provideCsvImporter(): CsvImporter = CsvImporter()

    @Provides
    @Singleton
    fun provideJsonExporter(gson: Gson): JsonExporter = JsonExporter(gson)
}
