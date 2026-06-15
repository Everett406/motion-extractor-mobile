package com.everett.motionextractor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.everett.motionextractor.ui.theme.GlassTint
import com.everett.motionextractor.ui.theme.Primary
import com.everett.motionextractor.ui.theme.SurfaceElevated
import io.github.alexzhirkevich.cupertino.CupertinoButton
import io.github.alexzhirkevich.cupertino.CupertinoButtonDefaults
import io.github.alexzhirkevich.cupertino.CupertinoText

@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, (value, label) ->
            val isSelected = value == selected
            val shape = when (index) {
                0 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp)
                options.lastIndex -> RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp)
                else -> RoundedCornerShape(0.dp)
            }
            CupertinoButton(
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f),
                shape = shape,
                colors = if (isSelected) {
                    CupertinoButtonDefaults.filledButtonColors(containerColor = Primary)
                } else {
                    CupertinoButtonDefaults.plainButtonColors(
                        containerColor = SurfaceElevated.copy(alpha = 0.5f),
                        contentColor = Color.White
                    )
                },
                border = if (isSelected) null else BorderStroke(0.5.dp, GlassTint.copy(alpha = 0.5f)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
            ) {
                CupertinoText(text = label)
            }
        }
    }
}
