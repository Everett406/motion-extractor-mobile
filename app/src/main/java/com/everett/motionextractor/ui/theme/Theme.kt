package com.everett.motionextractor.ui.theme

import androidx.compose.runtime.Composable
import io.github.alexzhirkevich.cupertino.theme.CupertinoTheme
import io.github.alexzhirkevich.cupertino.theme.darkColorScheme

@Composable
fun MotionExtractorTheme(
    content: @Composable () -> Unit
) {
    CupertinoTheme(
        colorScheme = darkColorScheme(),
        content = content
    )
}
