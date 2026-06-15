package com.everett.motionextractor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.everett.motionextractor.ui.theme.Surface

@Composable
fun PreviewArea(
    bitmaps: List<Bitmap>,
    showPlayer: Boolean,
    player: Player?,
    videoInfo: VideoInfoSummary?,
    modifier: Modifier = Modifier
) {
    // Fixed: Adaptive aspect ratio based on video orientation
    val aspectRatio = rememberVideoAspectRatio(videoInfo)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(Surface),
        contentAlignment = Alignment.Center
    ) {
        if (showPlayer && player != null) {
            // Fixed: AndroidView is a Composable, cannot be inside remember block
            // Use AndroidView directly at Composable level
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        useController = true
                    }
                },
                update = { view ->
                    // Only update player if changed
                    if (view.player != player) {
                        view.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (bitmaps.isNotEmpty()) {
            if (bitmaps.size == 1) {
                Image(
                    bitmap = bitmaps[0].asImageBitmap(),
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
                    bitmaps.forEach { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "预览帧",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
                                .padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberVideoAspectRatio(videoInfo: VideoInfoSummary?): Float {
    return if (videoInfo != null) {
        val isPortrait = videoInfo.rotationDegrees == 90 || videoInfo.rotationDegrees == 270
        if (isPortrait) {
            // Portrait: 9:16 or actual ratio
            videoInfo.height.toFloat() / videoInfo.width.coerceAtLeast(1)
        } else {
            // Landscape: 16:9 or actual ratio
            videoInfo.width.toFloat() / videoInfo.height.coerceAtLeast(1)
        }
    } else {
        // Default 16:9 when no video selected
        16f / 9f
    }
}
