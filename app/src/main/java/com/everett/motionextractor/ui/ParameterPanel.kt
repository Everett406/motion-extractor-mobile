package com.everett.motionextractor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.everett.motionextractor.OutputMode
import com.everett.motionextractor.ui.components.CupertinoCapsuleButton
import com.everett.motionextractor.ui.components.ParameterSection
import com.everett.motionextractor.ui.components.SegmentedControl
import com.everett.motionextractor.ui.theme.TextSecondary
import io.github.alexzhirkevich.cupertino.CupertinoSlider
import io.github.alexzhirkevich.cupertino.CupertinoSwitch
import io.github.alexzhirkevich.cupertino.CupertinoText

@Composable
fun ParameterPanel(
    state: UiState,
    onStateChange: (UiState) -> Unit,
    onPreset: (Preset) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Fixed: Presets with correct selected state
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Preset.entries.forEach { preset ->
                // Fixed: selected = state.currentPreset == preset
                CupertinoCapsuleButton(
                    onClick = { onPreset(preset) },
                    selected = state.currentPreset == preset
                ) {
                    CupertinoText(text = preset.label)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Output mode
        CupertinoText(text = "输出模式", color = TextSecondary)
        Spacer(modifier = Modifier.height(6.dp))
        SegmentedControl(
            options = listOf(
                OutputMode.COLOR to "彩色",
                OutputMode.GRAYSCALE to "灰度",
                OutputMode.INVERTED to "反色"
            ),
            selected = state.outputMode,
            onSelect = { onStateChange(state.copy(outputMode = it)) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Basic section with animation
        ParameterSection(
            title = "基础参数",
            expanded = state.basicExpanded,
            onToggle = { onStateChange(state.copy(basicExpanded = !state.basicExpanded)) }
        ) {
            AnimatedVisibility(
                visible = state.basicExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    SwitchRow(
                        label = "反色偏移层",
                        checked = state.invert,
                        onCheckedChange = { onStateChange(state.copy(invert = it)) }
                    )
                    SliderRow(
                        label = "帧偏移",
                        value = state.offsetFrames,
                        valueRange = 1f..120f,
                        onValueChange = { onStateChange(state.copy(offsetFrames = it)) }
                    )
                    SliderRow(
                        label = "不透明度",
                        value = state.opacity,
                        valueRange = 0f..1f,
                        onValueChange = { onStateChange(state.copy(opacity = it)) }
                    )
                }
            }
        }

        // Enhance section with animation
        ParameterSection(
            title = "增强效果",
            expanded = state.enhanceExpanded,
            onToggle = { onStateChange(state.copy(enhanceExpanded = !state.enhanceExpanded)) }
        ) {
            AnimatedVisibility(
                visible = state.enhanceExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    SliderRow(
                        label = "高斯模糊",
                        value = state.blurRadius,
                        valueRange = 0f..20f,
                        onValueChange = { onStateChange(state.copy(blurRadius = it)) }
                    )
                    SliderRow(
                        label = "发光半径",
                        value = state.glowRadius,
                        valueRange = 0f..50f,
                        onValueChange = { onStateChange(state.copy(glowRadius = it)) }
                    )
                    SliderRow(
                        label = "发光强度",
                        value = state.glowIntensity,
                        valueRange = 0f..3f,
                        onValueChange = { onStateChange(state.copy(glowIntensity = it)) }
                    )
                    SliderRow(
                        label = "对比度",
                        value = state.contrast,
                        valueRange = 0f..5f,
                        onValueChange = { onStateChange(state.copy(contrast = it)) }
                    )
                    SliderRow(
                        label = "亮度",
                        value = state.brightness,
                        valueRange = -0.5f..0.5f,
                        onValueChange = { onStateChange(state.copy(brightness = it)) }
                    )
                }
            }
        }

        // RGB section with animation
        ParameterSection(
            title = "RGB 拖影",
            expanded = state.rgbExpanded,
            onToggle = { onStateChange(state.copy(rgbExpanded = !state.rgbExpanded)) }
        ) {
            AnimatedVisibility(
                visible = state.rgbExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    SwitchRow(
                        label = "RGB 通道分离拖影",
                        checked = state.useRgbOffsets,
                        onCheckedChange = { onStateChange(state.copy(useRgbOffsets = it)) }
                    )
                    SliderRow(
                        label = "红色偏移",
                        value = state.rgbOffsetR,
                        valueRange = 0f..30f,
                        enabled = state.useRgbOffsets,
                        onValueChange = { onStateChange(state.copy(rgbOffsetR = it)) }
                    )
                    SliderRow(
                        label = "绿色偏移",
                        value = state.rgbOffsetG,
                        valueRange = 0f..30f,
                        enabled = state.useRgbOffsets,
                        onValueChange = { onStateChange(state.copy(rgbOffsetG = it)) }
                    )
                    SliderRow(
                        label = "蓝色偏移",
                        value = state.rgbOffsetB,
                        valueRange = 0f..30f,
                        enabled = state.useRgbOffsets,
                        onValueChange = { onStateChange(state.copy(rgbOffsetB = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CupertinoText(text = label)
        CupertinoSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        CupertinoText(text = "$label: ${formatValue(value)}", color = TextSecondary)
        CupertinoSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatValue(value: Float): String {
    return if (value == value.toInt().toFloat()) value.toInt().toString() else "%.2f".format(value)
}

enum class Preset(val label: String) {
    CLASSIC("经典"),
    NEON("霓虹"),
    HIGH_CONTRAST("高对比"),
    SOFT("柔和"),
    RESET("重置")
}
