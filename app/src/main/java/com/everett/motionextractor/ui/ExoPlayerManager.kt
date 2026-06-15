package com.everett.motionextractor.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

fun createExoPlayer(context: Context): ExoPlayer {
    return ExoPlayer.Builder(context)
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
}

fun ExoPlayer.setVideoUri(uri: Uri) {
    setMediaItem(MediaItem.fromUri(uri))
    prepare()
}

val LocalExoPlayer = staticCompositionLocalOf<ExoPlayer?> { null }
