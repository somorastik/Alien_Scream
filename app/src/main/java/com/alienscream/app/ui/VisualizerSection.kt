package com.alienscream.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alienscream.app.engine.VisualizerMode
import com.alienscream.app.engine.computeFFT

@Composable
fun Visualizer(
    waveform: FloatArray,
    mode: VisualizerMode,
    onModeChange: (VisualizerMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF102019), RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (mode == VisualizerMode.OSCILLOSCOPE) "Oscilloscope" else "Spectrum (FFT)",
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFFE3FFF0)
            )

            Row(
                modifier = Modifier
                    .background(Color(0xFF0D1913), RoundedCornerShape(8.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                VisualizerMode.entries.forEach { m ->
                    val isSelected = m == mode
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .padding(horizontal = 8.dp)
                            .background(
                                if (isSelected) Color(0xFF2D5B46) else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onModeChange(m) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (m == VisualizerMode.OSCILLOSCOPE) "OSC" else "FFT",
                            color = if (isSelected) Color(0xFF7CFFD0) else Color(0xFF9DD9B5),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        if (mode == VisualizerMode.OSCILLOSCOPE) {
            OscilloscopeCanvas(waveform = waveform)
        } else {
            FftCanvas(waveform = waveform)
        }
    }
}

@Composable
fun OscilloscopeCanvas(waveform: FloatArray) {
    // Find zero-crossing to stabilize the display
    val triggeredWaveform = remember(waveform) {
        var triggerIndex = 0
        // Look for a zero crossing (positive slope) in the first half of the buffer
        for (i in 1 until waveform.size / 2) {
            if (waveform[i - 1] < 0 && waveform[i] >= 0) {
                triggerIndex = i
                break
            }
        }
        // Return a slice starting from the trigger index
        val displaySize = waveform.size / 2
        if (triggerIndex + displaySize <= waveform.size) {
            waveform.copyOfRange(triggerIndex, triggerIndex + displaySize)
        } else {
            waveform.copyOfRange(0, displaySize)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(top = 8.dp)
            .background(Color(0xFF06100B), RoundedCornerShape(8.dp))
    ) {
        val midY = size.height / 2f
        val gridColor = Color(0xFF1C4030)
        repeat(4) { index ->
            val y = size.height * (index + 1) / 5f
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }
        drawLine(
            color = Color(0xFF2D5B46),
            start = Offset(0f, midY),
            end = Offset(size.width, midY),
            strokeWidth = 2f
        )

        if (triggeredWaveform.isEmpty()) return@Canvas

        val pathPoints = triggeredWaveform.mapIndexed { index, sample ->
            val x = index.toFloat() / (triggeredWaveform.size - 1).coerceAtLeast(1) * size.width
            val y = ((1f - ((sample.coerceIn(-1f, 1f) + 1f) / 2f)) * size.height)
            Offset(x, y)
        }

        for (i in 1 until pathPoints.size) {
            drawLine(
                color = Color(0xFF7CFFD0),
                start = pathPoints[i - 1],
                end = pathPoints[i],
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun FftCanvas(waveform: FloatArray) {
    val magnitudes = remember(waveform) { computeFFT(waveform) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(top = 8.dp)
            .background(Color(0xFF06100B), RoundedCornerShape(8.dp))
    ) {
        val width = size.width
        val height = size.height
        val binCount = magnitudes.size
        val barWidth = width / binCount

        for (i in 0 until binCount) {
            val mag = magnitudes[i] * 10f
            val barHeight = (mag.coerceIn(0f, 1f) * height)
            
            drawLine(
                color = Color(0xFF7CFFD0),
                start = Offset(i * barWidth + barWidth / 2, height),
                end = Offset(i * barWidth + barWidth / 2, height - barHeight),
                strokeWidth = barWidth.coerceAtLeast(1f)
            )
        }
    }
}
