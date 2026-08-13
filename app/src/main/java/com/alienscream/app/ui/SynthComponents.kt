package com.alienscream.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alienscream.app.engine.WaveShape
import kotlin.math.roundToInt

@Composable
fun WaveShapeSelector(
    selected: WaveShape,
    onShapeSelected: (WaveShape) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF102019), RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Text(
            text = "Wave Shape",
            style = MaterialTheme.typography.titleSmall,
            color = Color(0xFFE3FFF0),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WaveShape.entries.forEach { shape ->
                val isSelected = shape == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(
                            if (isSelected) Color(0xFF2D5B46) else Color(0xFF0D1913),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onShapeSelected(shape) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shape.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = if (isSelected) Color(0xFF7CFFD0) else Color(0xFF9DD9B5),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun SynthSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit,
    compact: Boolean = false
) {
    val horizontalPadding = if (compact) 8.dp else 12.dp
    val verticalPadding = if (compact) 4.dp else 8.dp
    val labelStyle = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleSmall
    val valueStyle = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium
    val sliderHeight = if (compact) 24.dp else 32.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF102019), RoundedCornerShape(12.dp))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = labelStyle,
                color = Color(0xFFE3FFF0),
                maxLines = 1
            )
            Text(
                text = "${value.roundToInt()} $unit",
                style = valueStyle,
                color = Color(0xFF9DD9B5),
                maxLines = 1
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.height(sliderHeight)
        )
    }
}
