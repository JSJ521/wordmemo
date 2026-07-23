package com.wordmemo.app.data.shadowing.repository

import com.wordmemo.app.data.shadowing.dao.ShadowingRecordDao
import com.wordmemo.app.data.shadowing.dao.ShadowingSentenceDao
import com.wordmemo.app.data.shadowing.dao.ShadowingVideoDao
import com.wordmemo.app.data.shadowing.entity.ShadowingRecordEntity
import com.wordmemo.app.data.shadowing.entity.ShadowingSentenceEntity
import com.wordmemo.app.data.shadowing.mapper.toDomain
import com.wordmemo.app.data.shadowing.mapper.toEntity
import com.wordmemo.app.domain.shadowing.model.ShadowingRecord
import com.wordmemo.app.domain.shadowing.model.ShadowingSentence
import com.wordmemo.app.domain.shadowing.model.ShadowingVideo
import com.wordmemo.app.domain.shadowing.repository.ShadowingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShadowingRepositoryImpl @Inject constructor(
    private val videoDao: ShadowingVideoDao,
    private val sentenceDao: ShadowingSentenceDao,
    private val recordDao: ShadowingRecordDao
) : ShadowingRepository {

    override fun observeVideos(): Flow<List<ShadowingVideo>> =
        videoDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getVideoById(videoId: Long): ShadowingVideo? =
        videoDao.getById(videoId)?.toDomain()

    override suspend fun deleteVideo(videoId: Long) {
        videoDao.deleteById(videoId)
    }

    override fun getSentencesForVideo(videoId: Long): Flow<List<ShadowingSentence>> =
        sentenceDao.observeByVideoId(videoId).map { list -> list.map { it.toDomain() } }

    override suspend fun updateSentence(sentence: ShadowingSentence) {
        sentenceDao.update(sentence.toEntity())
    }

    override suspend fun saveRecording(
        videoId: Long,
        sentenceId: Long,
        audioFilePath: String,
        durationMs: Long
    ): Long {
        val entity = ShadowingRecordEntity(
            videoId = videoId,
            sentenceId = sentenceId,
            audioFilePath = audioFilePath,
            durationMs = durationMs
        )
        return recordDao.insert(entity)
    }

    override suspend fun getRecordingsForSentence(sentenceId: Long): List<ShadowingRecord> =
        recordDao.getBySentenceId(sentenceId).map { it.toDomain() }

    override suspend fun deleteRecording(recordId: Long) {
        recordDao.deleteById(recordId)
    }

    override suspend fun insertSentences(sentences: List<ShadowingSentence>) {
        sentenceDao.insertBatch(sentences.map { it.toEntity() })
    }

    override suspend fun updateVideoDuration(videoId: Long, durationMs: Long) {
        videoDao.updateDuration(videoId, durationMs)
    }

    override suspend fun updateVideoSubtitlePath(videoId: Long, subtitlePath: String) {
        videoDao.updateSubtitlePath(videoId, subtitlePath)
    }
}
