package com.everett.motionextractor.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.everett.motionextractor.R
import com.everett.motionextractor.ui.theme.Background
import com.everett.motionextractor.ui.theme.Danger
import com.everett.motionextractor.ui.theme.Primary
import com.everett.motionextractor.ui.theme.SurfaceElevated
import com.everett.motionextractor.ui.theme.TextPrimary
import com.everett.motionextractor.ui.theme.TextSecondary
import io.github.alexzhirkevich.cupertino.CupertinoButton
import io.github.alexzhirkevich.cupertino.CupertinoButtonDefaults
import io.github.alexzhirkevich.cupertino.CupertinoText

@Composable
fun HomeScreen(
    onPickVideo: () -> Unit,
    onVideoSelected: (Uri) -> Unit,
    selectedUri: Uri?,
    openCvReady: Boolean
) {
    // If video was already selected (e.g. from activity result), navigate to editor
    LaunchedEffect(selectedUri) {
        selectedUri?.let { onVideoSelected(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App icon / logo area
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Logo",
                    modifier = Modifier.size(80.dp),
                    tint = Primary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            CupertinoText(
                text = "Motion Extractor",
                color = TextPrimary,
                fontSize = 32.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle
            CupertinoText(
                text = "提取视频中的运动轨迹\n创造独特的视觉效果",
                color = TextSecondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Main action button
            CupertinoButton(
                onClick = onPickVideo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CupertinoButtonDefaults.filledButtonColors(containerColor = Primary)
            ) {
                CupertinoText(
                    text = "选择视频",
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // OpenCV status
            if (!openCvReady) {
                CupertinoText(
                    text = "⚠️ OpenCV 初始化失败，部分功能可能不可用",
                    color = Danger,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
