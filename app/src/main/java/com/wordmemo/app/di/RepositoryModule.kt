package com.wordmemo.app.di

import com.wordmemo.app.data.repository.*
import com.wordmemo.app.data.shadowing.repository.ShadowingRepositoryImpl
import com.wordmemo.app.data.pronunciation.repository.PronunciationRepositoryImpl
import com.wordmemo.app.domain.repository.*
import com.wordmemo.app.domain.shadowing.repository.ShadowingRepository
import com.wordmemo.app.domain.pronunciation.repository.PronunciationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindWordRepository(impl: WordRepositoryImpl): WordRepository

    @Binds
    @Singleton
    abstract fun bindReviewRepository(impl: ReviewRepositoryImpl): ReviewRepository

    @Binds
    @Singleton
    abstract fun bindGroupRepository(impl: GroupRepositoryImpl): GroupRepository

    @Binds
    @Singleton
    abstract fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository

    @Binds
    @Singleton
    abstract fun bindAiRepository(impl: AiRepositoryImpl): AiRepository

    // M1 影子跟读
    @Binds
    @Singleton
    abstract fun bindShadowingRepository(impl: ShadowingRepositoryImpl): ShadowingRepository

    // M2 发音测评
    @Binds
    @Singleton
    abstract fun bindPronunciationRepository(impl: PronunciationRepositoryImpl): PronunciationRepository
}
