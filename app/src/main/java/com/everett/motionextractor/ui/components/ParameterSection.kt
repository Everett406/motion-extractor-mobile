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
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CupertinoText(
                text = "${if (expanded) "▼" else "▶"} $title",
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }
        if (expanded) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                content()
            }
        }
    }
}
