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
    data class AiMnemonics(val wordId: Long) : Screen("ai_mnemonics/{wordId}", "AI 助记") {
        companion object {
            const val ROUTE = "ai_mnemonics/{wordId}"
            fun createRoute(wordId: Long) = "ai_mnemonics/$wordId"
        }
    }
    data class AiRelations(val wordId: Long) : Screen("ai_relations/{wordId}", "关联图谱") {
        companion object {
            const val ROUTE = "ai_relations/{wordId}"
            fun createRoute(wordId: Long) = "ai_relations/$wordId"
        }
    }
    data class WordGraph(val wordId: Long) : Screen("word_graph/{wordId}", "单词图谱") {
        companion object {
            const val ROUTE = "word_graph/{wordId}"
            fun createRoute(wordId: Long) = "word_graph/$wordId"
        }
    }
    data object Stats : Screen("stats", "统计", Icons.Default.BarChart)
}
