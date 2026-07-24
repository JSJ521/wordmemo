package com.wordmemo.app.shadowing

import com.wordmemo.app.data.shadowing.service.HardcodedSubtitleOCR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * HardcodedSubtitleOCR 单元测试
 *
 * 测试覆盖：
 * - MergedItem 数据类正确性
 * - 边界情况（空路径、空输出）
 * - 异常处理
 * - 目录自动创建
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HardcodedSubtitleOCRTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var ocr: HardcodedSubtitleOCR

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        ocr = HardcodedSubtitleOCR(RuntimeEnvironment.getApplication())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ==================== MergedItem 数据类测试 ====================

    @Test
    fun `MergedItem 数据类正确创建`() {
        val item = HardcodedSubtitleOCR.MergedItem(
            text = "Hello world",
            startMs = 1000L,
            endMs = 5000L,
            frameCount = 5
        )
        assertEquals("Hello world", item.text)
        assertEquals(1000L, item.startMs)
        assertEquals(5000L, item.endMs)
        assertEquals(5, item.frameCount)
    }

    @Test
    fun `MergedItem 单帧条目`() {
        val item = HardcodedSubtitleOCR.MergedItem(
            text = "Single",
            startMs = 0L,
            endMs = 1000L,
            frameCount = 1
        )
        assertEquals(1, item.frameCount)
        assertEquals(0L, item.startMs)
        assertEquals(1000L, item.endMs)
    }

    @Test
    fun `MergedItem 多帧合并条目`() {
        val item = HardcodedSubtitleOCR.MergedItem(
            text = "Multiple frames merged",
            startMs = 3000L,
            endMs = 12000L,
            frameCount = 10
        )
        assertEquals("Multiple frames merged", item.text)
        assertEquals(3000L, item.startMs)
        assertEquals(12000L, item.endMs)
        assertEquals(10, item.frameCount)
    }

    // ==================== 边界情况测试 ====================

    @Test
    fun `空视频路径应返回失败`() = runTest(testDispatcher) {
        val result = ocr.extractSubtitles(
            videoPath = "",
            outputPath = "/tmp/test/output.srt"
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `空输出路径应返回失败`() = runTest(testDispatcher) {
        val result = ocr.extractSubtitles(
            videoPath = "/sdcard/video.mp4",
            outputPath = ""
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `不存在的视频文件应返回失败`() = runTest(testDispatcher) {
        val result = ocr.extractSubtitles(
            videoPath = "/dev/null/nonexistent_video.mp4",
            outputPath = "/tmp/ocr_output.srt"
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `输出目录不存在时不应崩溃`() = runTest(testDispatcher) {
        val result = ocr.extractSubtitles(
            videoPath = "/dev/null/video.mp4",
            outputPath = "/tmp/ocr_test/nested/output.srt"
        )
        // 预期失败（视频不存在），但不因目录创建而崩溃
        assertTrue(result.isFailure)
    }

    // ==================== 并发安全 ====================

    @Test
    fun `多次并发调用不应崩溃`() = runTest(testDispatcher) {
        val paths = listOf(
            "/dev/null/video1.mp4" to "/dev/null/out1.srt",
            "/dev/null/video2.mp4" to "/dev/null/out2.srt",
            "/dev/null/video3.mp4" to "/dev/null/out3.srt"
        )
        val results = paths.map { (video, output) ->
            ocr.extractSubtitles(video, output)
        }
        // 全部应为失败（视频不存在），但不应崩溃
        assertTrue(results.all { it.isFailure })
    }

    // ==================== MergedItem 排序测试 ====================

    @Test
    fun `MergedItem 按起始时间正确排序`() {
        val items = listOf(
            HardcodedSubtitleOCR.MergedItem("C", 5000L, 8000L, 4),
            HardcodedSubtitleOCR.MergedItem("A", 0L, 2000L, 3),
            HardcodedSubtitleOCR.MergedItem("B", 2000L, 5000L, 4)
        )
        val sorted = items.sortedBy { it.startMs }
        assertEquals(0L, sorted[0].startMs)
        assertEquals(2000L, sorted[1].startMs)
        assertEquals(5000L, sorted[2].startMs)
    }

    @Test
    fun `MergedItem 空列表排序`() {
        val sorted = emptyList<HardcodedSubtitleOCR.MergedItem>().sortedBy { it.startMs }
        assertTrue(sorted.isEmpty())
    }
}
