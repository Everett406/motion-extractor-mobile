package com.everett.motionextractor.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.everett.motionextractor.ui.LocalExoPlayer
import com.everett.motionextractor.ui.setVideoUri
import com.everett.motionextractor.ui.theme.Background
import com.everett.motionextractor.ui.theme.Primary
import com.everett.motionextractor.ui.theme.SurfaceElevated
import com.everett.motionextractor.ui.theme.TextPrimary
import com.everett.motionextractor.ui.theme.TextSecondary
import io.github.alexzhirkevich.cupertino.CupertinoButton
import io.github.alexzhirkevich.cupertino.CupertinoButtonDefaults
import io.github.alexzhirkevich.cupertino.CupertinoText
import java.io.File

@Composable
fun ResultScreen(
    outputFile: File?,
    onSaveToGallery: (File) -> Unit,
    onReEdit: () -> Unit,
    onBackToHome: () -> Unit
) {
    val exoPlayer = LocalExoPlayer.current
    val context = LocalContext.current

    // Load output video into player
    LaunchedEffect(outputFile) {
        outputFile?.let { file ->
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            exoPlayer?.setVideoUri(uri)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CupertinoButton(
                    onClick = onBackToHome,
                    colors = CupertinoButtonDefaults.plainButtonColors()
                ) {
                    CupertinoText(text = "← 首页", color = Primary)
                }

                CupertinoText(
                    text = "导出完成",
                    color = TextPrimary,
                    fontSize = 18.sp
                )

                // Spacer for alignment
                Box(modifier = Modifier.padding(horizontal = 16.dp))
            }

            // Video player
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(SurfaceElevated)
            ) {
                outputFile?.let { _ ->
                    exoPlayer?.let { player ->
                        AndroidView(
                            factory = { ctx ->
                                androidx.media3.ui.PlayerView(ctx).apply {
                                    this.player = player
                                    useController = true
                                }
                            },
                            update = { it.player = player },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } ?: run {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CupertinoText(
                            text = "没有可播放的视频",
                            color = TextSecondary
                        )
                    }
                }
            }

            // Action buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CupertinoButton(
                        onClick = { outputFile?.let { onSaveToGallery(it) } },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CupertinoButtonDefaults.filledButtonColors(containerColor = Primary)
                    ) {
                        CupertinoText(text = "保存到相册")
                    }
                    CupertinoButton(
                        onClick = onReEdit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CupertinoButtonDefaults.filledButtonColors(containerColor = SurfaceElevated)
                    ) {
                        CupertinoText(text = "重新编辑")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                CupertinoButton(
                    onClick = onBackToHome,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CupertinoButtonDefaults.plainButtonColors()
                ) {
                    CupertinoText(text = "返回首页", color = TextSecondary)
                }
            }
        }
    }
}
