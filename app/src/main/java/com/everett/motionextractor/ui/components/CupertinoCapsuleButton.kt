package com.everett.motionextractor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
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

@Composable
fun CupertinoCapsuleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    CupertinoButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        colors = if (selected) {
            CupertinoButtonDefaults.filledButtonColors(containerColor = Primary)
        } else {
            CupertinoButtonDefaults.plainButtonColors(
                containerColor = SurfaceElevated.copy(alpha = 0.6f),
                contentColor = Color.White
            )
        },
        border = if (selected) null else BorderStroke(
            width = 0.5.dp,
            color = GlassTint.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        content = content
    )
}
