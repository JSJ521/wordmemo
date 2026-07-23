package com.wordmemo.app.pronunciation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wordmemo.app.data.local.WordMemoDatabase
import com.wordmemo.app.data.pronunciation.dao.AssessmentRecordDao
import com.wordmemo.app.data.pronunciation.dao.PhonemeScoreDao
import com.wordmemo.app.data.pronunciation.repository.PronunciationRepositoryImpl
import com.wordmemo.app.domain.pronunciation.model.AssessmentRecord
import com.wordmemo.app.domain.pronunciation.model.PhonemeScore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * PronunciationRepositoryImpl 集成测试 — 使用 Room in-memory 数据库。
 * 验证测评记录的存储、查询以及音素分数联动。
 */
@RunWith(RobolectricTestRunner::class)
class PronunciationRepositoryImplTest {

    private lateinit var database: WordMemoDatabase
    private lateinit var assessmentRecordDao: AssessmentRecordDao
    private lateinit var phonemeScoreDao: PhonemeScoreDao
    private lateinit var repository: PronunciationRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WordMemoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        assessmentRecordDao = database.assessmentRecordDao()
        phonemeScoreDao = database.phonemeScoreDao()
        repository = PronunciationRepositoryImpl(assessmentRecordDao, phonemeScoreDao)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `observeAll 返回空列表当无记录时`() = runBlocking {
        val assessments = repository.observeAll().first()
        assertTrue("无记录时应返回空列表", assessments.isEmpty())
    }

    @Test
    fun `insert 插入测评记录并返回ID`() = runBlocking {
        val record = AssessmentRecord(
            sentenceText = "Hello world",
            overallScore = 95,
            scoreLevel = "优秀",
            assessmentType = "shadowing"
        )

        val id = repository.insert(record)
        assertTrue("插入后应返回正ID", id > 0)
    }

    @Test
    fun `insert 后 observeAll 返回插入的记录`() = runBlocking {
        val record = AssessmentRecord(
            sentenceText = "Test sentence",
            overallScore = 85,
            scoreLevel = "良好",
            assessmentType = "shadowing"
        )

        repository.insert(record)

        val assessments = repository.observeAll().first()
        assertEquals(1, assessments.size)
        assertEquals("Test sentence", assessments[0].sentenceText)
        assertEquals(85, assessments[0].overallScore)
        assertEquals("良好", assessments[0].scoreLevel)
    }

    @Test
    fun `insert 插入带音素分数的记录`() = runBlocking {
        val phonemeScores = listOf(
            PhonemeScore(phoneme = "th", gopScore = 0.85, colorTag = "green"),
            PhonemeScore(phoneme = "r", gopScore = 0.45, colorTag = "red", correctionHint = "舌尖需卷起")
        )

        val record = AssessmentRecord(
            sentenceText = "The red car",
            overallScore = 75,
            scoreLevel = "良好",
            assessmentType = "shadowing",
            phonemeScores = phonemeScores
        )

        val id = repository.insert(record)
        assertTrue("ID应大于0", id > 0)

        // 验证音素分数级联存储
        val loaded = repository.getById(id)
        assertNotNull(loaded)
        assertEquals("应加载关联的音素分数", 2, loaded!!.phonemeScores.size)
        assertEquals("th", loaded.phonemeScores[0].phoneme)
        assertEquals("red", loaded.phonemeScores[1].colorTag)
    }

    @Test
    fun `getById 返回正确的测评记录`() = runBlocking {
        val record = AssessmentRecord(
            sentenceText = "Find me",
            overallScore = 90,
            scoreLevel = "优秀",
            assessmentType = "shadowing"
        )

        val id = repository.insert(record)

        val loaded = repository.getById(id)
        assertNotNull(loaded)
        assertEquals("Find me", loaded!!.sentenceText)
        assertEquals(90, loaded.overallScore)
    }

    @Test
    fun `getById 返回null当记录不存在时`() = runBlocking {
        val loaded = repository.getById(999L)
        assertNull(loaded)
    }

    @Test
    fun `observeBySentenceId 按句子ID过滤`() = runBlocking {
        val record1 = AssessmentRecord(
            sentenceText = "First sentence",
            overallScore = 80,
            scoreLevel = "良好",
            assessmentType = "shadowing",
            sentenceId = 1L
        )

        val record2 = AssessmentRecord(
            sentenceText = "Second sentence",
            overallScore = 70,
            scoreLevel = "一般",
            assessmentType = "shadowing",
            sentenceId = 2L
        )

        repository.insert(record1)
        repository.insert(record2)

        val forSentence1 = repository.observeBySentenceId(1L).first()
        assertEquals(1, forSentence1.size)
        assertEquals("First sentence", forSentence1[0].sentenceText)

        val forSentence2 = repository.observeBySentenceId(2L).first()
        assertEquals(1, forSentence2.size)
        assertEquals("Second sentence", forSentence2[0].sentenceText)
    }

    @Test
    fun `多个测评记录按创建时间降序返回`() = runBlocking {
        val record1 = AssessmentRecord(
            sentenceText = "First",
            overallScore = 90,
            scoreLevel = "优秀",
            assessmentType = "shadowing"
        )

        val record2 = AssessmentRecord(
            sentenceText = "Second",
            overallScore = 80,
            scoreLevel = "良好",
            assessmentType = "shadowing"
        )

        repository.insert(record1)
        repository.insert(record2)

        val all = repository.observeAll().first()
        assertTrue(all.size >= 2)
        // 最新的在前
        assertEquals("Second", all[0].sentenceText)
        assertEquals("First", all[1].sentenceText)
    }

    @Test
    fun `getById 包含关联的音素分数`() = runBlocking {
        val phonemeScores = listOf(
            PhonemeScore(phoneme = "s", gopScore = 0.9, colorTag = "green"),
            PhonemeScore(phoneme = "h", gopScore = 0.7, colorTag = "yellow", correctionHint = "需更柔和"),
            PhonemeScore(phoneme = "t", gopScore = 0.95, colorTag = "green")
        )

        val record = AssessmentRecord(
            sentenceText = "Shop",
            overallScore = 85,
            scoreLevel = "良好",
            assessmentType = "shadowing",
            phonemeScores = phonemeScores
        )

        val id = repository.insert(record)

        val loaded = repository.getById(id)
        assertNotNull(loaded)
        val r = loaded!!
        assertEquals(3, r.phonemeScores.size)
        assertEquals("s", r.phonemeScores[0].phoneme)
        assertEquals("需更柔和", r.phonemeScores[1].correctionHint)
        assertEquals("green", r.phonemeScores[2].colorTag)

        // Verify all scores are associated with the correct assessment ID
        r.phonemeScores.forEach { score ->
            assertEquals("音素分数应关联到正确的assessmentId", id, score.assessmentId)
        }
    }
}
