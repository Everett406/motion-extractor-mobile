package com.everett.motionextractor.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.everett.motionextractor.OutputMode
import com.everett.motionextractor.VideoProcessor
import com.everett.motionextractor.ui.LocalExoPlayer
import com.everett.motionextractor.ui.Preset
import com.everett.motionextractor.ui.VideoInfoSummary
import com.everett.motionextractor.ui.theme.Background
import com.everett.motionextractor.ui.theme.Danger
import com.everett.motionextractor.ui.theme.GlassBackground
import com.everett.motionextractor.ui.theme.Primary
import com.everett.motionextractor.ui.theme.SurfaceElevated
import com.everett.motionextractor.ui.theme.TextPrimary
import com.everett.motionextractor.ui.theme.TextSecondary
import com.everett.motionextractor.ui.theme.TextTertiary
import io.github.alexzhirkevich.cupertino.CupertinoButton
import io.github.alexzhirkevich.cupertino.CupertinoButtonDefaults
import io.github.alexzhirkevich.cupertino.CupertinoSlider
import io.github.alexzhirkevich.cupertino.CupertinoSwitch
import io.github.alexzhirkevich.cupertino.CupertinoText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import java.io.File

@Composable
fun EditorScreen(
    lifecycleScope: LifecycleCoroutineScope,
    videoProcessor: VideoProcessor,
    selectedUri: Uri?,
    openCvReady: Boolean,
    onExportComplete: (File) -> Unit,
    onBack: () -> Unit
) {
    var state by remember { mutableStateOf(EditorState()) }
    val exoPlayer = LocalExoPlayer.current

    // Load video info
    LaunchedEffect(selectedUri) {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            EditorTopBar(
                onBack = onBack,
                videoInfo = state.videoInfo
            )

            // Preview area
            PreviewSection(
                state = state,
                exoPlayer = exoPlayer,
                onTogglePlay = { state = state.copy(showPlayer = !state.showPlayer) }
            )

            // Bottom panel with presets and parameters
            BottomPanel(
                state = state,
                onStateChange = { state = it },
                onPreview = {
                    selectedUri?.let { uri ->
                        state = state.copy(isProcessing = true, statusText = "生成预览中...")
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
                                statusText = ""
                            )
                        }
                    }
                },
                onExport = {
                    selectedUri?.let { uri ->
                        state = state.copy(
                            isProcessing = true,
                            statusText = "导出中...",
                            progress = 0f
                        )
                        lifecycleScope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    videoProcessor.exportVideo(uri, state.toMotionExtractParams()) { p ->
                                        state = state.copy(progress = p)
                                    }
                                }
                                state = state.copy(isProcessing = false)
                                onExportComplete(file)
                            } catch (e: Exception) {
                                state = state.copy(
                                    isProcessing = false,
                                    statusText = "导出失败: ${e.message}"
                                )
                            }
                        }
                    }
                },
                canProcess = openCvReady && selectedUri != null
            )
        }

        // Progress overlay
        if (state.isProcessing) {
            ProcessingOverlay(
                progress = state.progress,
                statusText = state.statusText
            )
        }
    }
}

@Composable
private fun EditorTopBar(
    onBack: () -> Unit,
    videoInfo: VideoInfoSummary?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CupertinoButton(
            onClick = onBack,
            colors = CupertinoButtonDefaults.plainButtonColors()
        ) {
            CupertinoText(text = "← 返回", color = Primary)
        }

        videoInfo?.let {
            val isPortrait = it.rotationDegrees == 90 || it.rotationDegrees == 270
            val w = if (isPortrait) it.height else it.width
            val h = if (isPortrait) it.width else it.height
            CupertinoText(
                text = "${w}×${h}",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun PreviewSection(
    state: EditorState,
    exoPlayer: Player?,
    onTogglePlay: () -> Unit
) {
    val aspectRatio = rememberVideoAspectRatio(state.videoInfo)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(SurfaceElevated)
            .clickable(enabled = state.outputFile != null, onClick = onTogglePlay),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.showPlayer && exoPlayer != null && state.outputFile != null -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = exoPlayer
                            useController = true
                        }
                    },
                    update = { it.player = exoPlayer },
                    modifier = Modifier.fillMaxSize()
                )
            }
            state.previewBitmaps.isNotEmpty() -> {
                if (state.previewBitmaps.size == 1) {
                    Image(
                        bitmap = state.previewBitmaps[0].asImageBitmap(),
                        contentDescription = "预览",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        state.previewBitmaps.forEach { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(bmp.width.toFloat() / bmp.height.coerceAtLeast(1))
                                    .padding(horizontal = 2.dp)
                            )
                        }
                    }
                }
            }
            else -> {
                CupertinoText(
                    text = "选择参数后点击「生成预览」",
                    color = TextTertiary
                )
            }
        }
    }
}

@Composable
private fun BottomPanel(
    state: EditorState,
    onStateChange: (EditorState) -> Unit,
    onPreview: () -> Unit,
    onExport: () -> Unit,
    canProcess: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Presets
        PresetBar(
            currentPreset = state.currentPreset,
            onPresetSelected = { preset ->
                onStateChange(applyPreset(state, preset).copy(currentPreset = preset))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Output mode
        OutputModeSelector(
            selected = state.outputMode,
            onSelect = { onStateChange(state.copy(outputMode = it)) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Parameter sections
        ParameterSections(state, onStateChange)

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CupertinoButton(
                onClick = onPreview,
                enabled = canProcess && !state.isProcessing,
                modifier = Modifier.weight(1f),
                colors = CupertinoButtonDefaults.filledButtonColors(containerColor = SurfaceElevated)
            ) {
                CupertinoText(text = "生成预览")
            }
            CupertinoButton(
                onClick = onExport,
                enabled = canProcess && !state.isProcessing,
                modifier = Modifier.weight(1f),
                colors = CupertinoButtonDefaults.filledButtonColors(containerColor = Primary)
            ) {
                CupertinoText(text = "导出视频")
            }
        }
    }
}

@Composable
private fun PresetBar(
    currentPreset: Preset?,
    onPresetSelected: (Preset) -> Unit
) {
    val presets = listOf(
        Preset.CLASSIC to "经典",
        Preset.NEON to "霓虹",
        Preset.HIGH_CONTRAST to "高对比",
        Preset.SOFT to "柔和",
        Preset.RESET to "重置"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { (preset, label) ->
            val selected = currentPreset == preset
            val color = when (preset) {
                Preset.CLASSIC -> com.everett.motionextractor.ui.theme.PresetClassic
                Preset.NEON -> com.everett.motionextractor.ui.theme.PresetNeon
                Preset.HIGH_CONTRAST -> com.everett.motionextractor.ui.theme.PresetHighContrast
                Preset.SOFT -> com.everett.motionextractor.ui.theme.PresetSoft
                Preset.RESET -> TextTertiary
            }

            CupertinoButton(
                onClick = { onPresetSelected(preset) },
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(18.dp),
                colors = if (selected) {
                    CupertinoButtonDefaults.filledButtonColors(containerColor = color)
                } else {
                    CupertinoButtonDefaults.plainButtonColors(
                        containerColor = SurfaceElevated.copy(alpha = 0.5f)
                    )
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                CupertinoText(
                    text = label,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun OutputModeSelector(
    selected: OutputMode,
    onSelect: (OutputMode) -> Unit
) {
    val modes = listOf(
        OutputMode.COLOR to "彩色",
        OutputMode.GRAYSCALE to "灰度",
        OutputMode.INVERTED to "反色"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        modes.forEach { (mode, label) ->
            val isSelected = mode == selected
            CupertinoButton(
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = if (isSelected) {
                    CupertinoButtonDefaults.filledButtonColors(containerColor = Primary)
                } else {
                    CupertinoButtonDefaults.plainButtonColors(
                        containerColor = SurfaceElevated.copy(alpha = 0.5f)
                    )
                }
            ) {
                CupertinoText(text = label, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ParameterSections(
    state: EditorState,
    onStateChange: (EditorState) -> Unit
) {
    // Basic
    ExpandableSection(
        title = "基础参数",
        expanded = state.basicExpanded,
        onToggle = { onStateChange(state.copy(basicExpanded = !state.basicExpanded)) }
    ) {
        Column {
            SwitchRow("反色偏移层", state.invert) {
                onStateChange(state.copy(invert = it))
            }
            SliderRow("帧偏移", state.offsetFrames, 1f..120f) {
                onStateChange(state.copy(offsetFrames = it))
            }
            SliderRow("不透明度", state.opacity, 0f..1f) {
                onStateChange(state.copy(opacity = it))
            }
        }
    }

    // Enhance
    ExpandableSection(
        title = "增强效果",
        expanded = state.enhanceExpanded,
        onToggle = { onStateChange(state.copy(enhanceExpanded = !state.enhanceExpanded)) }
    ) {
        Column {
            SliderRow("高斯模糊", state.blurRadius, 0f..20f) {
                onStateChange(state.copy(blurRadius = it))
            }
            SliderRow("发光半径", state.glowRadius, 0f..50f) {
                onStateChange(state.copy(glowRadius = it))
            }
            SliderRow("发光强度", state.glowIntensity, 0f..3f) {
                onStateChange(state.copy(glowIntensity = it))
            }
            SliderRow("对比度", state.contrast, 0f..5f) {
                onStateChange(state.copy(contrast = it))
            }
            SliderRow("亮度", state.brightness, -0.5f..0.5f) {
                onStateChange(state.copy(brightness = it))
            }
        }
    }

    // RGB
    ExpandableSection(
        title = "RGB 拖影",
        expanded = state.rgbExpanded,
        onToggle = { onStateChange(state.copy(rgbExpanded = !state.rgbExpanded)) }
    ) {
        Column {
            SwitchRow("RGB 通道分离拖影", state.useRgbOffsets) {
                onStateChange(state.copy(useRgbOffsets = it))
            }
            SliderRow("红色偏移", state.rgbOffsetR, 0f..30f, enabled = state.useRgbOffsets) {
                onStateChange(state.copy(rgbOffsetR = it))
            }
            SliderRow("绿色偏移", state.rgbOffsetG, 0f..30f, enabled = state.useRgbOffsets) {
                onStateChange(state.copy(rgbOffsetG = it))
            }
            SliderRow("蓝色偏移", state.rgbOffsetB, 0f..30f, enabled = state.useRgbOffsets) {
                onStateChange(state.copy(rgbOffsetB = it))
            }
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CupertinoText(
                text = if (expanded) "▼" else "▶",
                color = TextSecondary,
                modifier = Modifier.padding(end = 8.dp)
            )
            CupertinoText(
                text = title,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CupertinoText(text = label, color = TextPrimary)
        CupertinoSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CupertinoText(text = label, color = TextSecondary, fontSize = 14.sp)
            CupertinoText(
                text = "%.1f".format(value),
                color = if (enabled) Primary else TextTertiary,
                fontSize = 14.sp
            )
        }
        CupertinoSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ProcessingOverlay(
    progress: Float,
    statusText: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                CupertinoText(
                    text = "${(progress * 100).toInt()}%",
                    color = Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            CupertinoText(
                text = statusText,
                color = TextPrimary
            )

            if (progress > 0f) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Primary,
                    trackColor = SurfaceElevated
                )
            }
        }
    }
}

@Composable
private fun rememberVideoAspectRatio(videoInfo: VideoInfoSummary?): Float {
    return if (videoInfo != null) {
        val isPortrait = videoInfo.rotationDegrees == 90 || videoInfo.rotationDegrees == 270
        if (isPortrait) {
            videoInfo.height.toFloat() / videoInfo.width.coerceAtLeast(1)
        } else {
            videoInfo.width.toFloat() / videoInfo.height.coerceAtLeast(1)
        }
    } else {
        16f / 9f
    }
}

private fun rotateMat(mat: Mat, degrees: Int): Mat {
    return when (degrees) {
        90 -> Mat().also { Core.rotate(mat, it, Core.ROTATE_90_CLOCKWISE) }
        180 -> Mat().also { Core.rotate(mat, it, Core.ROTATE_180) }
        270 -> Mat().also { Core.rotate(mat, it, Core.ROTATE_90_COUNTERCLOCKWISE) }
        else -> mat
    }
}

// State for editor screen
data class EditorState(
    val videoInfo: VideoInfoSummary? = null,
    val isProcessing: Boolean = false,
    val statusText: String = "",
    val progress: Float = 0f,
    val previewBitmaps: List<Bitmap> = emptyList(),
    val showPlayer: Boolean = false,
    val outputFile: File? = null,

    // Parameters
    val offsetFrames: Float = 3f,
    val invert: Boolean = true,
    val opacity: Float = 0.5f,
    val blurRadius: Float = 0f,
    val glowRadius: Float = 0f,
    val glowIntensity: Float = 0.5f,
    val contrast: Float = 1.5f,
    val brightness: Float = 0f,
    val outputMode: OutputMode = OutputMode.GRAYSCALE,
    val useRgbOffsets: Boolean = false,
    val rgbOffsetR: Float = 0f,
    val rgbOffsetG: Float = 0f,
    val rgbOffsetB: Float = 0f,

    // Sections
    val basicExpanded: Boolean = true,
    val enhanceExpanded: Boolean = false,
    val rgbExpanded: Boolean = false,

    val currentPreset: Preset? = null
)

fun EditorState.toMotionExtractParams() = com.everett.motionextractor.MotionExtractParams(
    offsetFrames = offsetFrames.toInt(),
    invert = invert,
    opacity = opacity.toDouble(),
    blurRadius = blurRadius.toDouble(),
    glowRadius = glowRadius.toDouble(),
    glowIntensity = glowIntensity.toDouble(),
    contrast = contrast.toDouble(),
    brightness = brightness.toDouble(),
    mode = outputMode,
    useRgbOffsets = useRgbOffsets,
    rgbOffsets = com.everett.motionextractor.RgbOffsets(
        r = rgbOffsetR.toInt(),
        g = rgbOffsetG.toInt(),
        b = rgbOffsetB.toInt()
    )
)

fun applyPreset(state: EditorState, preset: Preset): EditorState = when (preset) {
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
