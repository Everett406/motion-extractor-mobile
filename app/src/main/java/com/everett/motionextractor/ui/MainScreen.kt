package com.everett.motionextractor.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleCoroutineScope
import com.everett.motionextractor.OutputMode
import com.everett.motionextractor.VideoProcessor
import com.everett.motionextractor.ui.theme.Background
import com.everett.motionextractor.ui.theme.GlassTint
import com.everett.motionextractor.ui.theme.SurfaceElevated
import com.everett.motionextractor.ui.theme.TextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import io.github.alexzhirkevich.cupertino.CupertinoButton
import io.github.alexzhirkevich.cupertino.CupertinoButtonDefaults
import io.github.alexzhirkevich.cupertino.CupertinoScaffold
import io.github.alexzhirkevich.cupertino.CupertinoText
import io.github.alexzhirkevich.cupertino.CupertinoTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import java.io.File

@Composable
fun MainScreen(
    lifecycleScope: LifecycleCoroutineScope,
    videoProcessor: VideoProcessor,
    onPickVideo: () -> Unit,
    onSaveToGallery: (File) -> Unit,
    selectedUri: Uri?,
    openCvReady: Boolean,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf(UiState()) }
    val hazeState = remember { HazeState() }
    val exoPlayer = LocalExoPlayer.current

    // When selectedUri arrives from Activity result, reset state and load info
    LaunchedEffect(selectedUri) {
        if (selectedUri == state.selectedUri) return@LaunchedEffect
        state = state.copy(
            selectedUri = selectedUri,
            showPlayer = false,
            outputFile = null,
            previewBitmaps = emptyList(),
            videoInfo = null
        )
        selectedUri?.let { uri ->
            val info = withContext(Dispatchers.IO) { videoProcessor.getVideoInfo(uri) }
            info?.let {
                state = state.copy(
                    videoInfo = VideoInfoSummary(
                        width = it.width,
                        height = it.height,
                        fps = it.fps,
                        durationMs = it.durationMs,
                        rotationDegrees = it.rotationDegrees
                    )
                )
            }
        }
    }

    val context = LocalContext.current

    // When output file changes and user taps play, load it into ExoPlayer
    LaunchedEffect(state.showPlayer, state.outputFile) {
        val outputFile = state.outputFile
        if (state.showPlayer && outputFile != null && exoPlayer != null) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )
            exoPlayer.setVideoUri(uri)
        }
    }

    CupertinoScaffold(
        topBar = {
            CupertinoTopAppBar(
                title = { CupertinoText(text = "Motion Extractor") }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        // Fixed: Background is now clear, only the floating panel uses hazeChild
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Preview area with adaptive aspect ratio
                PreviewArea(
                    bitmaps = state.previewBitmaps,
                    showPlayer = state.showPlayer,
                    player = exoPlayer,
                    videoInfo = state.videoInfo,
                    modifier = Modifier.fillMaxWidth()
                )

                // Video info display
                state.videoInfo?.let { info ->
                    Spacer(modifier = Modifier.height(12.dp))
                    val orientation = if (info.rotationDegrees == 90 || info.rotationDegrees == 270) " 竖屏" else ""
                    CupertinoText(
                        text = "分辨率: ${info.width}×${info.height}  帧率: %.2f  时长: %.1fs$orientation".format(info.fps, info.durationMs / 1000.0),
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                ActionBar(
                    onPickVideo = onPickVideo,
                    onPreview = {
                        val uri = state.selectedUri ?: return@ActionBar
                        state = state.copy(isProcessing = true, statusText = "正在生成预览...", showPlayer = false)
                        lifecycleScope.launch {
                            val frames = withContext(Dispatchers.IO) {
                                videoProcessor.generatePreviewFrames(uri, state.toMotionExtractParams())
                            }
                            val bitmaps = frames.map { mat ->
                                val rotated = rotateMat(mat, state.videoInfo?.rotationDegrees ?: 0)
                                val bmp = Bitmap.createBitmap(rotated.cols(), rotated.rows(), Bitmap.Config.ARGB_8888)
                                Utils.matToBitmap(rotated, bmp)
                                if (rotated !== mat) rotated.release()
                                mat.release()
                                bmp
                            }
                            state = state.copy(
                                previewBitmaps = bitmaps,
                                isProcessing = false,
                                statusText = "预览完成"
                            )
                        }
                    },
                    onExport = {
                        val uri = state.selectedUri ?: return@ActionBar
                        state = state.copy(
                            isProcessing = true,
                            statusText = "正在导出视频...",
                            progress = 0f,
                            showPlayer = false
                        )
                        val job = lifecycleScope.launch {
                            try {
                                val outputFile = withContext(Dispatchers.IO) {
                                    videoProcessor.exportVideo(uri, state.toMotionExtractParams()) { progress ->
                                        state = state.copy(
                                            progress = progress,
                                            statusText = "导出进度: ${(progress * 100).toInt()}%"
                                        )
                                    }
                                }
                                state = state.copy(
                                    outputFile = outputFile,
                                    isProcessing = false,
                                    statusText = "导出完成: ${outputFile.name}",
                                    exportJob = null
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                                state = state.copy(
                                    isProcessing = false,
                                    statusText = "导出失败: ${e.message}",
                                    exportJob = null
                                )
                            }
                        }
                        state = state.copy(exportJob = job)
                    },
                    onSave = {
                        state.outputFile?.let { onSaveToGallery(it) }
                    },
                    onPlay = {
                        state = state.copy(showPlayer = true)
                    },
                    onCancelExport = {
                        state.exportJob?.cancel()
                        state = state.copy(
                            isProcessing = false,
                            statusText = "导出已取消",
                            exportJob = null
                        )
                    },
                    canPreview = openCvReady && state.selectedUri != null,
                    canExport = openCvReady && state.selectedUri != null,
                    canSave = state.outputFile != null,
                    canPlay = state.outputFile != null,
                    isProcessing = state.isProcessing,
                    progress = state.progress
                )

                // Status and progress
                if (state.statusText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CupertinoText(
                        text = state.statusText,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Fixed: Parameter panel as floating bottom sheet with hazeChild
            // Only the panel uses hazeChild, background stays clear
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .hazeChild(
                        state = hazeState,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        style = HazeStyle(
                            tint = GlassTint,
                            blurRadius = 24.dp,
                            noiseFactor = 0.15f
                        )
                    )
                    .background(SurfaceElevated.copy(alpha = 0.7f))
            ) {
                ParameterPanel(
                    state = state,
                    onStateChange = { state = it },
                    onPreset = { preset ->
                        state = applyPreset(state, preset).copy(currentPreset = preset)
                    },
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

private fun applyPreset(state: UiState, preset: Preset): UiState = when (preset) {
    Preset.CLASSIC -> state.copy(
        offsetFrames = 3f, invert = true, opacity = 0.5f,
        blurRadius = 0f, glowRadius = 0f, glowIntensity = 0.5f,
        contrast = 1.5f, brightness = 0f, outputMode = OutputMode.GRAYSCALE,
        useRgbOffsets = false, rgbOffsetR = 0f, rgbOffsetG = 0f, rgbOffsetB = 0f
    )
    Preset.NEON -> state.copy(
        offsetFrames = 3f, invert = true, opacity = 0.6f,
        blurRadius = 0f, glowRadius = 8f, glowIntensity = 1.2f,
        contrast = 1.5f, brightness = 0f, outputMode = OutputMode.COLOR,
        useRgbOffsets = true, rgbOffsetR = 2f, rgbOffsetG = 4f, rgbOffsetB = 6f
    )
    Preset.HIGH_CONTRAST -> state.copy(
        offsetFrames = 2f, invert = true, opacity = 0.5f,
        blurRadius = 0f, glowRadius = 0f, glowIntensity = 0.5f,
        contrast = 3f, brightness = 0f, outputMode = OutputMode.GRAYSCALE,
        useRgbOffsets = false, rgbOffsetR = 0f, rgbOffsetG = 0f, rgbOffsetB = 0f
    )
    Preset.SOFT -> state.copy(
        offsetFrames = 5f, invert = true, opacity = 0.4f,
        blurRadius = 3f, glowRadius = 12f, glowIntensity = 0.4f,
        contrast = 0.8f, brightness = 0.05f, outputMode = OutputMode.COLOR,
        useRgbOffsets = true, rgbOffsetR = 3f, rgbOffsetG = 5f, rgbOffsetB = 7f
    )
    Preset.RESET -> state.copy(
        offsetFrames = 3f, invert = true, opacity = 0.5f,
        blurRadius = 0f, glowRadius = 0f, glowIntensity = 0.5f,
        contrast = 1.5f, brightness = 0f, outputMode = OutputMode.GRAYSCALE,
        useRgbOffsets = false, rgbOffsetR = 0f, rgbOffsetG = 0f, rgbOffsetB = 0f,
        currentPreset = null
    )
}

private fun rotateMat(mat: Mat, degrees: Int): Mat {
    return when (degrees) {
        90 -> Mat().also { Core.rotate(mat, it, Core.ROTATE_90_CLOCKWISE) }
        180 -> Mat().also { Core.rotate(mat, it, Core.ROTATE_180) }
        270 -> Mat().also { Core.rotate(mat, it, Core.ROTATE_90_COUNTERCLOCKWISE) }
        else -> mat
    }
}
