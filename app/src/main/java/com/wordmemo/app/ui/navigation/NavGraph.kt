package com.wordmemo.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.wordmemo.app.ui.screen.addword.AddWordScreen
import com.wordmemo.app.ui.screen.ailearning.AiLearningScreen
import com.wordmemo.app.ui.screen.groups.GroupsScreen
import com.wordmemo.app.ui.screen.ocr.OcrScreen
import com.wordmemo.app.ui.screen.review.ReviewSessionScreen
import com.wordmemo.app.ui.screen.settings.SettingsScreen
import com.wordmemo.app.ui.screen.stats.StatsScreen
import com.wordmemo.app.ui.screen.worddetail.WordDetailScreen
import com.wordmemo.app.ui.screen.wordgraph.WordGraphScreen
import com.wordmemo.app.ui.screen.wordlist.WordListScreen
import com.wordmemo.app.ui.screen.shadowing.ShadowingHomeScreen
import com.wordmemo.app.ui.screen.shadowing.ShadowingSessionScreen
import com.wordmemo.app.ui.screen.shadowing.EpubShadowingScreen
import com.wordmemo.app.ui.screen.pronunciation.AssessmentHomeScreen
import com.wordmemo.app.ui.screen.pronunciation.AssessmentResultScreen
import com.wordmemo.app.ui.screen.pronunciation.ProgressScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordmemo.app.ui.screen.reading.ReadingBookListScreen
import com.wordmemo.app.ui.screen.reading.ReadingScreen
import com.wordmemo.app.ui.screen.reading.ReadingViewModel

private val fadeSlideIn = slideInHorizontally(
    animationSpec = tween(280),
    initialOffsetX = { it / 4 }
) + fadeIn(animationSpec = tween(280))

private val fadeSlideOut = slideOutHorizontally(
    animationSpec = tween(280),
    targetOffsetX = { -(it / 4) }
) + fadeOut(animationSpec = tween(180))

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.WordList.route) {

        composable(
            Screen.WordList.route,
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) }
        ) { backStackEntry ->
            val groupId = backStackEntry.savedStateHandle.get<Long>("selectedGroupId")
            WordListScreen(
                onNavigateToAdd = { navController.navigate(Screen.AddWord.route) },
                onNavigateToWordDetail = { wordId ->
                    navController.navigate(Screen.WordDetail.createRoute(wordId))
                },
                onNavigateToReview = { navController.navigate(Screen.ReviewSession.route) },
                onNavigateToGroups = { navController.navigate(Screen.Groups.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToStats = { navController.navigate(Screen.Stats.route) },
                onNavigateToOcr = { navController.navigate(Screen.Ocr.route) },
                onNavigateToShadowing = { navController.navigate(Screen.ShadowingHome.route) },
                onNavigateToAssessment = { navController.navigate(Screen.AssessmentHome.route) },
                onNavigateToReading = { navController.navigate(Screen.ReadingBookList.route) },
                initialGroupId = groupId
            )
        }

        composable(Screen.AddWord.route,
            enterTransition = { fadeSlideIn },
            exitTransition = { fadeSlideOut }
        ) { AddWordScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.WordDetail.ROUTE,
            arguments = listOf(navArgument("wordId") { type = NavType.LongType }),
            enterTransition = { fadeSlideIn },
            exitTransition = { fadeSlideOut }
        ) { backStackEntry ->
            val wordId = backStackEntry.arguments?.getLong("wordId") ?: return@composable
            WordDetailScreen(
                wordId = wordId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAiLearning = {
                    navController.navigate(Screen.AiLearning.createRoute(wordId))
                },
                onNavigateToGraph = {
                    navController.navigate(Screen.WordGraph.createRoute(wordId))
                }
            )
        }

        composable(Screen.ReviewSession.route,
            enterTransition = { fadeSlideIn },
            exitTransition = { fadeSlideOut }
        ) { ReviewSessionScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Groups.route,
            enterTransition = { fadeSlideIn },
            exitTransition = { fadeSlideOut }
        ) { GroupsScreen(
                onNavigateBack = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.remove<Long>("selectedGroupId")
                    navController.popBackStack()
                },
                onNavigateToGroup = { groupId ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selectedGroupId", groupId)
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route,
            enterTransition = { fadeSlideIn },
            exitTransition = { fadeSlideOut }
        ) { SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AiLearning.ROUTE,
            arguments = listOf(navArgument("wordId") { type = NavType.LongType }),
            enterTransition = { fadeIn(animationSpec = tween(250)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) }
        ) { backStackEntry ->
            val wordId = backStackEntry.arguments?.getLong("wordId") ?: return@composable
            AiLearningScreen(
                wordId = wordId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Stats.route,
            enterTransition = { fadeSlideIn },
            exitTransition = { fadeSlideOut }
        ) { StatsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.WordGraph.ROUTE,
            arguments = listOf(navArgument("wordId") { type = NavType.LongType }),
            enterTransition = { fadeIn(animationSpec = tween(250)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) }
        ) { backStackEntry ->
            val wordId = backStackEntry.arguments?.getLong("wordId") ?: return@composable
            WordGraphScreen(
                wordId = wordId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Ocr.route,
            enterTransition = { fadeSlideIn },
            exitTransition = { fadeSlideOut }
        ) { OcrScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // S2 影子跟读
        composable(Screen.ShadowingHome.route,
            enterTransition = { fadeSlideIn },
            exitTransition = { fadeSlideOut }
        ) { ShadowingHomeScreen(
                onNavigateToSession = { videoId ->
                    navController.navigate(Screen.ShadowingSession.createRoute(videoId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ShadowingSession.ROUTE,
            arguments = listOf(navArgument("videoId") { type = NavType.LongType }),
            enterTransition = { fadeSlideIn },
            exitTransition = { fadeSlideOut }
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getLong("videoId") ?: return@composable
            ShadowingSessionScreen(
                videoId = videoId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // S2 发音测评
        composable(Screen.AssessmentHome.route,
            enterTransition = { fadeSlideIn },
            exitTransition = { fadeSlideOut }
        ) { AssessmentHomeScreen(
                onNavigateToResult = { assessmentId ->
                    navController.navigate(Screen.AssessmentResult.createRoute(assessmentId))
                },
                onNavigateToProgress = {
                    navController.navigate(Screen.Progress.route)
                },
                onNavigateToShadowing = {
                    navController.navigate(Screen.ShadowingHome.route)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AssessmentResult.ROUTE,
            arguments = listOf(navArgument("assessmentId") { type = NavType.LongType }),
            enterTransition = { fadeSlideIn },
            exitTransition = { fadeSlideOut }
        ) { backStackEntry ->
            val assessmentId = backStackEntry.arguments?.getLong("assessmentId") ?: return@composable
            AssessmentResultScreen(
                assessmentId = assessmentId,
                onNavigateToProgress = {
                    navController.navigate(Screen.Progress.route)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Progress.route,
            enterTransition = { fadeSlideIn },
            exitTransition = { fadeSlideOut }
        ) { ProgressScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // EPUB 精听
        composable(Screen.ReadingBookList.route,
            enterTransition = { fadeSlideIn },
            exitTransition = { fadeSlideOut }
        ) { ReadingBookListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReading = {
                    navController.navigate(Screen.ReadingPage.route)
                }
            )
        }

        composable(Screen.ReadingPage.route,
            enterTransition = { fadeSlideIn },
            exitTransition = { fadeSlideOut }
        ) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.ReadingBookList.route)
            }
            val viewModel: ReadingViewModel = hiltViewModel(parentEntry)
            ReadingScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToShadowing = { sentences ->
                    EpubShadowingDataHolder.sentences = sentences
                    navController.navigate(Screen.EpubShadowing.route)
                },
                viewModel = viewModel
            )
        }

        // EPUB 跟读页面
        composable(Screen.EpubShadowing.route,
            enterTransition = { fadeSlideIn },
            exitTransition = { fadeSlideOut }
        ) {
            val sentences = EpubShadowingDataHolder.sentences ?: emptyList()
            val bookTitle = EpubShadowingDataHolder.bookTitle
            EpubShadowingScreen(
                sentences = sentences,
                bookTitle = bookTitle,
                onNavigateBack = {
                    EpubShadowingDataHolder.sentences = null
                    navController.popBackStack()
                }
            )
        }
    }
}
