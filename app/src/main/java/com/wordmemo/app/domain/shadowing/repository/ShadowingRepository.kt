package com.wordmemo.app.domain.shadowing.repository

import com.wordmemo.app.domain.shadowing.model.ShadowingRecord
import com.wordmemo.app.domain.shadowing.model.ShadowingSentence
import com.wordmemo.app.domain.shadowing.model.ShadowingVideo
import kotlinx.coroutines.flow.Flow

interface ShadowingRepository {

    fun observeVideos(): Flow<List<ShadowingVideo>>

    suspend fun getVideoById(videoId: Long): ShadowingVideo?

    suspend fun deleteVideo(videoId: Long)

    fun getSentencesForVideo(videoId: Long): Flow<List<ShadowingSentence>>

    suspend fun updateSentence(sentence: ShadowingSentence)

    suspend fun saveRecording(
        videoId: Long,
        sentenceId: Long,
        audioFilePath: String,
        durationMs: Long
    ): Long

    suspend fun getRecordingsForSentence(sentenceId: Long): List<ShadowingRecord>

    suspend fun deleteRecording(recordId: Long)
}
