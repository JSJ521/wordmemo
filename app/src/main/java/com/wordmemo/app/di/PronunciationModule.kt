package com.wordmemo.app.di

import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.data.pronunciation.dao.AssessmentRecordDao
import com.wordmemo.app.data.pronunciation.dao.PhonemeScoreDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PronunciationModule {

    @Provides
    @Singleton
    fun provideAssessmentRecordDao(db: WordMemoDatabase): AssessmentRecordDao =
        db.assessmentRecordDao()

    @Provides
    @Singleton
    fun providePhonemeScoreDao(db: WordMemoDatabase): PhonemeScoreDao =
        db.phonemeScoreDao()
}
