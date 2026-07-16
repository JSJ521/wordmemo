package com.wordmemo.app

import com.wordmemo.app.domain.fsrs.FSRSState
import org.junit.Assert.*
import org.junit.Test

/**
 * 纯逻辑测试：不依赖 Android Context，纯 Kotlin 运行。
 */
class ReviewFlowTest {

    @Test
    fun `FSRSState fromValue 正确转换 New`() {
        assertEquals(FSRSState.NEW, FSRSState.fromValue("New"))
    }

    @Test
    fun `FSRSState fromValue 正确转换 NEW`() {
        assertEquals(FSRSState.NEW, FSRSState.fromValue("NEW"))
    }

    @Test
    fun `FSRSState fromValue 小写也兜底`() {
        assertEquals(FSRSState.NEW, FSRSState.fromValue("new"))
    }
}
