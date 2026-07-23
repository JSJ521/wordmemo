package com.wordmemo.app.shadowing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.data.shadowing.dao.ShadowingRecordDao
import com.wordmemo.app.data.shadowing.dao.ShadowingSentenceDao
import com.wordmemo.app.data.shadowing.dao.ShadowingVideoDao
import com.wordmemo.app.data.shadowing.entity.ShadowingRecordEntity
import com.wordmemo.app.data.shadowing.entity.ShadowingSentenceEntity
import com.wordmemo.app.data.shadowing.entity.ShadowingVideoEntity
import com.wordmemo.app.data.shadowing.repository.ShadowingRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ShadowingRepositoryImpl 集成测试 — 使用 Room in-memory 数据库。
 * 验证 DAO 和 Repository 的数据操作逻辑。
 */
@RunWith(RobolectricTestRunner::class)
class ShadowingRepositoryImplTest {

    private lateinit var database: WordMemoDatabase
    private lateinit var videoDao: ShadowingVideoDao
    private lateinit var sentenceDao: ShadowingSentenceDao
    private lateinit var recordDao: ShadowingRecordDao
    private lateinit var repository: ShadowingRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WordMemoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        videoDao = database.shadowingVideoDao()
        sentenceDao = database.shadowingSentenceDao()
        recordDao = database.shadowingRecordDao()
        repository = ShadowingRepositoryImpl(videoDao, sentenceDao, recordDao)
    }

    @After
    fun teardown() {
        database.close()
    }

    // ── Video CRUD ────────────────────────────────────────────

    @Test
    fun `observeVideos 返回空列表当无视频时`() = runBlocking {
        val videos = repository.observeVideos().first()
        assertTrue("无视频时应返回空列表", videos.isEmpty())
    }

    @Test
    fun `observeVideos 返回插入的视频`() = runBlocking {
        val entity = ShadowingVideoEntity(
            title = "测试视频",
            sourceType = "bilibili",
            sourceUrl = "https://bilibili.com/video/test",
            filePath = "/tmp/test.mp4",
            durationMs = 60000L
        )
        videoDao.insert(entity)

        val videos = repository.observeVideos().first()
        assertEquals(1, videos.size)
        assertEquals("测试视频", videos[0].title)
        assertEquals("bilibili", videos[0].sourceType)
    }

    @Test
    fun `getVideoById 返回正确的视频`() = runBlocking {
        val entity = ShadowingVideoEntity(
            title = "特定视频",
            sourceType = "local",
            filePath = "/tmp/specific.mp4",
            durationMs = 120000L
        )
        val id = videoDao.insert(entity)

        val video = repository.getVideoById(id)
        assertNotNull(video)
        assertEquals("特定视频", video!!.title)
        assertEquals(id, video.id)
    }

    @Test
    fun `getVideoById 返回 null 当视频不存在时`() = runBlocking {
        val video = repository.getVideoById(999L)
        assertNull(video)
    }

    @Test
    fun `deleteVideo 删除视频及其关联数据`() = runBlocking {
        val videoEntity = ShadowingVideoEntity(
            title = "待删除",
            sourceType = "local",
            filePath = "/tmp/del.mp4",
            durationMs = 30000L
        )
        val videoId = videoDao.insert(videoEntity)

        val sentenceEntity = ShadowingSentenceEntity(
            videoId = videoId,
            sentenceIndex = 0,
            text = "Hello world",
            startTimeMs = 0L,
            endTimeMs = 2000L
        )
        val sentenceId = sentenceDao.insertBatch(listOf(sentenceEntity)).first()

        recordDao.insert(ShadowingRecordEntity(
            videoId = videoId,
            sentenceId = sentenceId,
            audioFilePath = "/tmp/rec.wav",
            durationMs = 2000L
        ))

        repository.deleteVideo(videoId)

        assertNull(videoDao.getById(videoId))
        assertTrue("关联录音应被删除", recordDao.getBySentenceId(sentenceId).isEmpty())
    }

    // ── Sentence ──────────────────────────────────────────────

    @Test
    fun `getSentencesForVideo 返回按时间排序的句子`() = runBlocking {
        val videoId = videoDao.insert(ShadowingVideoEntity(
            title = "句子测试",
            sourceType = "local",
            filePath = "/tmp/sent.mp4",
            durationMs = 10000L
        ))

        sentenceDao.insertBatch(listOf(
            ShadowingSentenceEntity(videoId = videoId, sentenceIndex = 0, text = "First", startTimeMs = 0L, endTimeMs = 1000L),
            ShadowingSentenceEntity(videoId = videoId, sentenceIndex = 1, text = "Second", startTimeMs = 1000L, endTimeMs = 2000L),
            ShadowingSentenceEntity(videoId = videoId, sentenceIndex = 2, text = "Third", startTimeMs = 2000L, endTimeMs = 3000L)
        ))

        val sentences = repository.getSentencesForVideo(videoId).first()
        assertEquals(3, sentences.size)
        assertEquals("First", sentences[0].text)
        assertEquals("Second", sentences[1].text)
        assertEquals("Third", sentences[2].text)
    }

    @Test
    fun `updateSentence 更新句子内容`() = runBlocking {
        val videoId = videoDao.insert(ShadowingVideoEntity(
            title = "句子更新",
            sourceType = "local",
            filePath = "/tmp/upd.mp4",
            durationMs = 5000L
        ))

        val ids = sentenceDao.insertBatch(listOf(
            ShadowingSentenceEntity(videoId = videoId, sentenceIndex = 0, text = "Original", startTimeMs = 0L, endTimeMs = 1000L)
        ))
        val sentenceId = ids.first()

        val sentences = repository.getSentencesForVideo(videoId).first()
        val updatedSentence = sentences[0].copy(text = "Updated", isMerged = true)
        repository.updateSentence(updatedSentence)

        val reloaded = repository.getSentencesForVideo(videoId).first()
        assertEquals("Updated", reloaded[0].text)
        assertEquals(true, reloaded[0].isMerged)
    }

    // ── Recording ─────────────────────────────────────────────

    @Test
    fun `saveRecording 保存录音记录`() = runBlocking {
        val videoId = videoDao.insert(ShadowingVideoEntity(
            title = "录音测试",
            sourceType = "local",
            filePath = "/tmp/rec_vid.mp4",
            durationMs = 10000L
        ))

        val sentenceIds = sentenceDao.insertBatch(listOf(
            ShadowingSentenceEntity(videoId = videoId, sentenceIndex = 0, text = "Record me", startTimeMs = 0L, endTimeMs = 2000L)
        ))

        val recordId = repository.saveRecording(
            videoId = videoId,
            sentenceId = sentenceIds.first(),
            audioFilePath = "/tmp/audio.wav",
            durationMs = 2000L
        )

        assertTrue("录音ID应大于0", recordId > 0)
    }

    @Test
    fun `getRecordingsForSentence 返回句子的录音列表`() = runBlocking {
        val videoId = videoDao.insert(ShadowingVideoEntity(
            title = "录音查询",
            sourceType = "local",
            filePath = "/tmp/rec_q.mp4",
            durationMs = 10000L
        ))

        val sentenceIds = sentenceDao.insertBatch(listOf(
            ShadowingSentenceEntity(videoId = videoId, sentenceIndex = 0, text = "Test", startTimeMs = 0L, endTimeMs = 2000L)
        ))
        val sentenceId = sentenceIds.first()

        val id1 = recordDao.insert(ShadowingRecordEntity(videoId = videoId, sentenceId = sentenceId, audioFilePath = "/tmp/a1.wav", durationMs = 1000L))
        val id2 = recordDao.insert(ShadowingRecordEntity(videoId = videoId, sentenceId = sentenceId, audioFilePath = "/tmp/a2.wav", durationMs = 1500L))

        val records = repository.getRecordingsForSentence(sentenceId)
        assertEquals(2, records.size)
        assertTrue("录音记录应包含id1", records.any { it.id == id1 })
        assertTrue("录音记录应包含id2", records.any { it.id == id2 })
    }

    @Test
    fun `deleteRecording 删除录音`() = runBlocking {
        val videoId = videoDao.insert(ShadowingVideoEntity(
            title = "删除录音",
            sourceType = "local",
            filePath = "/tmp/del_rec.mp4",
            durationMs = 5000L
        ))

        val sentenceIds = sentenceDao.insertBatch(listOf(
            ShadowingSentenceEntity(videoId = videoId, sentenceIndex = 0, text = "Delete", startTimeMs = 0L, endTimeMs = 2000L)
        ))

        val recordId = recordDao.insert(ShadowingRecordEntity(
            videoId = videoId, sentenceId = sentenceIds.first(),
            audioFilePath = "/tmp/del.wav", durationMs = 1000L
        ))

        repository.deleteRecording(recordId)

        val records = repository.getRecordingsForSentence(sentenceIds.first())
        assertTrue("删除后录音列表应为空", records.isEmpty())
    }
}
