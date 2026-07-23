package com.wordmemo.app.di

import com.wordmemo.app.data.shadowing.dao.ShadowingRecordDao
import com.wordmemo.app.data.shadowing.dao.ShadowingSentenceDao
import com.wordmemo.app.data.shadowing.dao.ShadowingVideoDao
import com.wordmemo.app.data.local.WordMemoDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object ShadowingModule {

    @Provides
    @Singleton
    fun provideShadowingVideoDao(db: WordMemoDatabase): ShadowingVideoDao =
        db.shadowingVideoDao()

    @Provides
    @Singleton
    fun provideShadowingSentenceDao(db: WordMemoDatabase): ShadowingSentenceDao =
        db.shadowingSentenceDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideShadowingRecordDao(db: WordMemoDatabase): ShadowingRecordDao =
        db.shadowingRecordDao()
}
