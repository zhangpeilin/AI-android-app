package com.example.comicreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.comicreader.ui.screens.ChapterListScreen
import com.example.comicreader.ui.screens.ComicListScreen
import com.example.comicreader.ui.screens.ReaderScreen
import com.example.comicreader.ui.screens.ServerSettingsScreen
import com.example.comicreader.ui.screens.WebDavBrowseScreen
import com.example.comicreader.ui.theme.ComicReaderTheme
import com.example.comicreader.viewmodel.ComicListViewModel
import com.example.comicreader.viewmodel.ComicReaderViewModel
import com.example.comicreader.viewmodel.ServerSettingsViewModel
import com.example.comicreader.viewmodel.WebDavBrowseViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComicReaderTheme {
                ComicReaderApp()
            }
        }
    }
}

@Composable
fun ComicReaderApp() {
    val navController = rememberNavController()
    val comicListViewModel: ComicListViewModel = viewModel()
    val comicReaderViewModel: ComicReaderViewModel = viewModel()
    val serverSettingsViewModel: ServerSettingsViewModel = viewModel()
    val webDavBrowseViewModel: WebDavBrowseViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "comic_list"
    ) {
        composable("comic_list") {
            ComicListScreen(
                viewModel = comicListViewModel,
                onComicClick = { comicId, title ->
                    navController.navigate("chapter_list/$comicId/$title")
                },
                onWebDavClick = {
                    navController.navigate("server_settings")
                }
            )
        }

        composable(
            route = "chapter_list/{comicId}/{title}",
            arguments = listOf(
                navArgument("comicId") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val comicId = backStackEntry.arguments?.getString("comicId") ?: ""
            val title = backStackEntry.arguments?.getString("title") ?: ""
            ChapterListScreen(
                comicId = comicId,
                comicTitle = title,
                viewModel = comicReaderViewModel,
                onBackClick = { navController.popBackStack() },
                onHomeClick = { navController.popBackStack("comic_list", false) },
                onChapterClick = { chapter ->
                    navController.navigate("reader/$comicId/$title/$chapter")
                }
            )
        }

        composable(
            route = "reader/{comicId}/{title}/{chapter}",
            arguments = listOf(
                navArgument("comicId") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("chapter") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val comicId = backStackEntry.arguments?.getString("comicId") ?: ""
            val title = backStackEntry.arguments?.getString("title") ?: ""
            val chapter = backStackEntry.arguments?.getString("chapter") ?: ""
            ReaderScreen(
                comicId = comicId,
                comicTitle = title,
                chapter = chapter,
                viewModel = comicReaderViewModel,
                onBackClick = { navController.popBackStack() },
                onHomeClick = { navController.popBackStack("comic_list", false) }
            )
        }

        composable("server_settings") {
            ServerSettingsScreen(
                viewModel = serverSettingsViewModel,
                onBackClick = { navController.popBackStack() },
                onServerClick = { serverId ->
                    navController.navigate("webdav_browse/$serverId")
                }
            )
        }

        composable(
            route = "webdav_browse/{serverId}",
            arguments = listOf(
                navArgument("serverId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
            WebDavBrowseScreen(
                serverId = serverId,
                viewModel = webDavBrowseViewModel,
                onBackClick = { navController.popBackStack() },
                onHomeClick = { navController.popBackStack("comic_list", false) },
                onComicClick = { comicId, title ->
                    navController.navigate("chapter_list/$comicId/$title")
                }
            )
        }
    }
}
