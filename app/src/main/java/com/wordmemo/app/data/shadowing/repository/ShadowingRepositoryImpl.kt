package com.wordmemo.app.data.shadowing.repository

import android.content.Context
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShadowingRepositoryImpl @Inject constructor(
    private val videoDao: ShadowingVideoDao,
    private val sentenceDao: ShadowingSentenceDao,
    private val recordDao: ShadowingRecordDao,
    @ApplicationContext private val context: Context
) : ShadowingRepository {

    override fun observeVideos(): Flow<List<ShadowingVideo>> =
        videoDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getVideoById(videoId: Long): ShadowingVideo? =
        videoDao.getById(videoId)?.toDomain()

    override suspend fun deleteVideo(videoId: Long) {
        // 先获取视频信息，用于清理文件
        val video = videoDao.getById(videoId)

        // 级联删除：先删记录，再删句子
        recordDao.deleteByVideoId(videoId)
        sentenceDao.deleteByVideoId(videoId)

        // 删除视频本身
        videoDao.deleteById(videoId)

        // 清理磁盘上的文件
        if (video != null) {
            listOfNotNull(
                video.filePath,
                video.coverPath,
                video.subtitlePath
            ).forEach { filePath ->
                try {
                    val file = File(filePath)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {
                    // 忽略单个文件的删除异常，不影响主流程
                }
            }
        }
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
