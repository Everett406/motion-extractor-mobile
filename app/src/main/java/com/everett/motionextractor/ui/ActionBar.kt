package com.everett.motionextractor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.everett.motionextractor.ui.theme.Primary
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
    canPreview: Boolean,
    canExport: Boolean,
    canSave: Boolean,
    canPlay: Boolean,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        CupertinoButton(
            onClick = onPickVideo,
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth(),
            colors = CupertinoButtonDefaults.filledButtonColors(containerColor = Primary)
        ) {
            CupertinoText(text = "选择视频")
        }

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
