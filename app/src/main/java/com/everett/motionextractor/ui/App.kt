package com.everett.motionextractor.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.everett.motionextractor.VideoProcessor
import com.everett.motionextractor.ui.navigation.Screen
import com.everett.motionextractor.ui.screens.EditorScreen
import com.everett.motionextractor.ui.screens.HomeScreen
import com.everett.motionextractor.ui.screens.ResultScreen
import java.io.File

@Composable
fun MotionExtractorApp(
    lifecycleScope: LifecycleCoroutineScope,
    videoProcessor: VideoProcessor,
    onPickVideo: () -> Unit,
    onSaveToGallery: (File) -> Unit,
    selectedUri: Uri?,
    openCvReady: Boolean
) {
    val navController = rememberNavController()
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }

    // When selectedUri changes from outside (e.g. activity result), update currentUri
    LaunchedEffect(selectedUri) {
        selectedUri?.let {
            currentUri = it
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onPickVideo = onPickVideo,
                onVideoSelected = { uri ->
                    currentUri = uri
                    navController.navigate(Screen.Editor.route)
                },
                selectedUri = currentUri,
                openCvReady = openCvReady
            )
        }

        composable(Screen.Editor.route) {
            EditorScreen(
                lifecycleScope = lifecycleScope,
                videoProcessor = videoProcessor,
                selectedUri = currentUri,
                openCvReady = openCvReady,
                onExportComplete = { file ->
                    outputFile = file
                    navController.navigate(Screen.Result.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Result.route) {
            ResultScreen(
                outputFile = outputFile,
                onSaveToGallery = onSaveToGallery,
                onReEdit = {
                    navController.popBackStack(Screen.Editor.route, false)
                },
                onBackToHome = {
                    currentUri = null
                    outputFile = null
                    navController.popBackStack(Screen.Home.route, false)
                }
            )
        }
    }
}
