package com.everett.motionextractor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.everett.motionextractor.ui.theme.Danger
import com.everett.motionextractor.ui.theme.Primary
import com.everett.motionextractor.ui.theme.TextSecondary
import io.github.alexzhirkevich.cupertino.CupertinoButton
import io.github.alexzhirkevich.cupertino.CupertinoButtonDefaults
import io.github.alexzhirkevich.cupertino.CupertinoText

@Composable
fun ActionBar(
    onPickVideo: () -> Unit,
    onPreview: () -> Unit,
    onExport: () -> Unit,
    onSave: () -> Unit,
    onPlay: () -> Unit,
    onCancelExport: () -> Unit,
    canPreview: Boolean,
    canExport: Boolean,
    canSave: Boolean,
    canPlay: Boolean,
    isProcessing: Boolean,
    progress: Float = 0f,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        // Primary action: Pick video
        CupertinoButton(
            onClick = onPickVideo,
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth(),
            colors = CupertinoButtonDefaults.filledButtonColors(containerColor = Primary)
        ) {
            CupertinoText(text = if (canPreview) "更换视频" else "选择视频")
        }

        // Secondary actions row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CupertinoButton(
                onClick = onPreview,
                enabled = canPreview && !isProcessing,
                modifier = Modifier.weight(1f)
            ) {
                CupertinoText(text = "生成预览")
            }
            CupertinoButton(
                onClick = onExport,
                enabled = canExport && !isProcessing,
                modifier = Modifier.weight(1f)
            ) {
                CupertinoText(text = "导出视频")
            }
        }

        // Fixed: Export progress bar with cancel button (using Material3 LinearProgressIndicator)
        if (isProcessing && progress > 0f) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Primary,
                    trackColor = TextSecondary.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CupertinoText(
                        text = "${(progress * 100).toInt()}%",
                        color = TextSecondary
                    )
                    CupertinoButton(
                        onClick = onCancelExport,
                        colors = CupertinoButtonDefaults.plainButtonColors(
                            contentColor = Danger
                        )
                    ) {
                        CupertinoText(text = "取消")
                    }
                }
            }
        }

        // Tertiary actions: Save and Play (only when output available)
        if (canSave || canPlay) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CupertinoButton(
                    onClick = onSave,
                    enabled = canSave && !isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    CupertinoText(text = "保存到相册")
                }
                CupertinoButton(
                    onClick = onPlay,
                    enabled = canPlay && !isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    CupertinoText(text = "播放")
                }
            }
        }
    }
}
