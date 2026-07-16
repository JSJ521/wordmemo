package com.wordmemo.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.wordmemo.app.ui.screen.addword.AddWordScreen
import com.wordmemo.app.ui.screen.aimnemonics.AiMnemonicsScreen
import com.wordmemo.app.ui.screen.airelations.AiRelationsScreen
import com.wordmemo.app.ui.screen.groups.GroupsScreen
import com.wordmemo.app.ui.screen.review.ReviewSessionScreen
import com.wordmemo.app.ui.screen.settings.SettingsScreen
import com.wordmemo.app.ui.screen.stats.StatsScreen
import com.wordmemo.app.ui.screen.worddetail.WordDetailScreen
import com.wordmemo.app.ui.screen.wordgraph.WordGraphScreen
import com.wordmemo.app.ui.screen.wordlist.WordListScreen

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
                onNavigateToMnemonics = {
                    navController.navigate(Screen.AiMnemonics.createRoute(wordId))
                },
                onNavigateToRelations = {
                    navController.navigate(Screen.AiRelations.createRoute(wordId))
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
            route = Screen.AiMnemonics.ROUTE,
            arguments = listOf(navArgument("wordId") { type = NavType.LongType }),
            enterTransition = { fadeIn(animationSpec = tween(250)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) }
        ) { backStackEntry ->
            val wordId = backStackEntry.arguments?.getLong("wordId") ?: return@composable
            AiMnemonicsScreen(
                wordId = wordId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AiRelations.ROUTE,
            arguments = listOf(navArgument("wordId") { type = NavType.LongType }),
            enterTransition = { fadeIn(animationSpec = tween(250)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) }
        ) { backStackEntry ->
            val wordId = backStackEntry.arguments?.getLong("wordId") ?: return@composable
            AiRelationsScreen(
                wordId = wordId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToWord = { targetWordId ->
                    navController.navigate(Screen.WordDetail.createRoute(targetWordId))
                }
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
    }
}
