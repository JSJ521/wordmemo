package com.wordmemo.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String = "", val icon: ImageVector? = null) {
    data object WordList : Screen("word_list", "单词本", Icons.Default.MenuBook)
    data object AddWord : Screen("add_word", "添加单词", Icons.Default.Add)
    data class WordDetail(val wordId: Long) : Screen("word_detail/{wordId}", "单词详情") {
        companion object {
            const val ROUTE = "word_detail/{wordId}"
            fun createRoute(wordId: Long) = "word_detail/$wordId"
        }
    }
    data object ReviewSession : Screen("review_session", "复习", Icons.Default.Loop)
    data object Groups : Screen("groups", "分组", Icons.Default.Folder)
    data object Settings : Screen("settings", "设置", Icons.Default.Settings)
    data class AiLearning(val wordId: Long) : Screen("ai_learning/{wordId}", "AI 学习") {
        companion object {
            const val ROUTE = "ai_learning/{wordId}"
            fun createRoute(wordId: Long) = "ai_learning/$wordId"
        }
    }
    data class WordGraph(val wordId: Long) : Screen("word_graph/{wordId}", "单词图谱") {
        companion object {
            const val ROUTE = "word_graph/{wordId}"
            fun createRoute(wordId: Long) = "word_graph/$wordId"
        }
    }
    data object Stats : Screen("stats", "统计", Icons.Default.BarChart)
    data object Ocr : Screen("ocr", "OCR扫描", Icons.Default.TextSnippet)

    // S2 影子跟读
    data object ShadowingHome : Screen("shadowing_home", "影子跟读", Icons.Default.PlayCircle)
    data class ShadowingSession(val videoId: Long) : Screen("shadowing_session/{videoId}", "跟读练习") {
        companion object {
            const val ROUTE = "shadowing_session/{videoId}"
            fun createRoute(videoId: Long) = "shadowing_session/$videoId"
        }
    }

    // S2 发音测评
    data object AssessmentHome : Screen("assessment_home", "发音测评", Icons.Default.RecordVoiceOver)
    data class AssessmentResult(val assessmentId: Long) : Screen("assessment_result/{assessmentId}", "测评结果") {
        companion object {
            const val ROUTE = "assessment_result/{assessmentId}"
            fun createRoute(assessmentId: Long) = "assessment_result/$assessmentId"
        }
    }
    data object Progress : Screen("progress", "学习进度", Icons.Default.TrendingUp)
}
