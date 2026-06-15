package com.everett.motionextractor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.everett.motionextractor.ui.theme.SurfaceElevated
import com.everett.motionextractor.ui.theme.TextPrimary
import com.everett.motionextractor.ui.theme.TextSecondary
import io.github.alexzhirkevich.cupertino.CupertinoText

@Composable
fun ParameterSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fixed: Better arrow indicator with color feedback
            CupertinoText(
                text = if (expanded) "▼" else "▶",
                color = TextSecondary,
                modifier = Modifier.padding(end = 8.dp)
            )
            CupertinoText(
                text = title,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }
        // Content is now handled by AnimatedVisibility in ParameterPanel
        content()
    }
}
