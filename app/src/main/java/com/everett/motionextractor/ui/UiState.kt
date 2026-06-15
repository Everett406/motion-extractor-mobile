package com.everett.motionextractor.ui

import android.net.Uri
import com.everett.motionextractor.MotionExtractParams
import com.everett.motionextractor.OutputMode
import com.everett.motionextractor.RgbOffsets
import java.io.File

data class UiState(
    val selectedUri: Uri? = null,
    val videoInfo: VideoInfoSummary? = null,
    val isProcessing: Boolean = false,
    val statusText: String = "",
    val progress: Float = 0f,
    val previewBitmaps: List<android.graphics.Bitmap> = emptyList(),
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
    val rgbExpanded: Boolean = false
)

data class VideoInfoSummary(
    val width: Int,
    val height: Int,
    val fps: Double,
    val durationMs: Long,
    val rotationDegrees: Int
)

fun UiState.toMotionExtractParams(): MotionExtractParams = MotionExtractParams(
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
    rgbOffsets = RgbOffsets(
        r = rgbOffsetR.toInt(),
        g = rgbOffsetG.toInt(),
        b = rgbOffsetB.toInt()
    )
)
