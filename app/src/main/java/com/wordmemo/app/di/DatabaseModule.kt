package com.wordmemo.app.di

import android.content.Context
import androidx.room.Room
import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.data.local.dao.*
import com.wordmemo.app.data.network.NetworkMonitor
import com.wordmemo.app.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): WordMemoDatabase =
        WordMemoDatabase.getInstance(ctx)

    @Provides fun provideWordDao(db: WordMemoDatabase): WordDao = db.wordDao()
    @Provides fun provideFlashcardDao(db: WordMemoDatabase): FlashcardDao = db.flashcardDao()
    @Provides fun provideReviewLogDao(db: WordMemoDatabase): ReviewLogDao = db.reviewLogDao()
    @Provides fun provideGroupDao(db: WordMemoDatabase): GroupDao = db.groupDao()
    @Provides fun provideTagDao(db: WordMemoDatabase): TagDao = db.tagDao()
    @Provides fun provideAppConfigDao(db: WordMemoDatabase): AppConfigDao = db.appConfigDao()
    @Provides fun provideAiContentDao(db: WordMemoDatabase): AiContentDao = db.aiContentDao()
    @Provides fun provideFsrsParamsDao(db: WordMemoDatabase): FsrsParamsDao = db.fsrsParamsDao()
}
