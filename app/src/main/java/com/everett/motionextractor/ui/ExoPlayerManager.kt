package com.everett.motionextractor.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class ExoPlayerManager(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }

    fun setVideoUri(uri: Uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
    }

    fun release() {
        player.release()
    }
}

// Extension function for ExoPlayer
fun ExoPlayer.setVideoUri(uri: Uri) {
    setMediaItem(MediaItem.fromUri(uri))
    prepare()
}

val LocalExoPlayer = staticCompositionLocalOf<ExoPlayer?> { null }
