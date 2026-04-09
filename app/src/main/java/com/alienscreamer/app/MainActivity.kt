package com.alienscreamer.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AlienScreamerApp()
                }
            }
        }
    }
}

private enum class WaveShape {
    SINE, TRIANGLE, SAWTOOTH, SQUARE
}

private enum class VisualizerMode {
    OSCILLOSCOPE, FFT
}

private class SynthEngine {
    @Volatile
    var vcoFrequencyHz: Float = 220f

    @Volatile
    var lfoDepthHz: Float = 80f

    @Volatile
    var lfoFrequencyHz: Float = 4f

    @Volatile
    var hardClippingEnabled: Boolean = false

    @Volatile
    var volume: Float = 0.7f

    @Volatile
    var waveShape: WaveShape = WaveShape.SINE

    @Volatile
    private var latestWaveform: FloatArray = FloatArray(BUFFER_SAMPLES)

    @Volatile
    private var running = false

    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null

    fun start() {
        if (running) return
        running = true

        audioThread = Thread(
            {
                runAudio()
            },
            "alien-screamer-android-audio"
        ).apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        audioThread?.join(500)
        audioThread = null
    }

    fun snapshotWaveform(): FloatArray = latestWaveform.copyOf()

    private fun runAudio() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val trackBufferSize = maxOf(minBufferSize, BUFFER_SAMPLES * 4)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(trackBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()

        val pcmBuffer = ShortArray(BUFFER_SAMPLES)
        var carrierPhase = 0.0
        var lfoPhase = 0.0

        try {
            while (running) {
                val scopeBuffer = FloatArray(BUFFER_SAMPLES)
                for (i in 0 until BUFFER_SAMPLES) {
                    val lfoValue = sin(lfoPhase) * lfoDepthHz
                    val frequency = maxOf(10f, vcoFrequencyHz + lfoValue.toFloat()).toDouble()
                    
                    val rawSample = when (waveShape) {
                        WaveShape.SINE -> sin(carrierPhase)
                        WaveShape.TRIANGLE -> {
                            val p = carrierPhase / (2.0 * PI)
                            if (p < 0.5) 4.0 * p - 1.0 else 3.0 - 4.0 * p
                        }
                        WaveShape.SAWTOOTH -> (carrierPhase / PI) - 1.0
                        WaveShape.SQUARE -> if (carrierPhase < PI) 1.0 else -1.0
                    }

                    var sample = rawSample * 0.25
                    if (hardClippingEnabled) {
                        sample = if (sample > 0.1) 0.1 else if (sample < -0.1) -0.1 else sample
                        sample *= 3.0 // Compensate for level loss
                    }

                    val finalSample = sample * volume

                    scopeBuffer[i] = finalSample.toFloat()
                    pcmBuffer[i] = (finalSample * Short.MAX_VALUE).roundToInt().toShort()

                    carrierPhase = wrapPhase(carrierPhase + (2.0 * PI * frequency / SAMPLE_RATE))
                    lfoPhase = wrapPhase(lfoPhase + (2.0 * PI * lfoFrequencyHz / SAMPLE_RATE))
                }

                latestWaveform = scopeBuffer
                track.write(pcmBuffer, 0, pcmBuffer.size)
            }
        } finally {
            track.stop()
            track.release()
            audioTrack = null
        }
    }

    private fun wrapPhase(value: Double): Double {
        return if (value >= 2.0 * PI) value - 2.0 * PI else value
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val BUFFER_SAMPLES = 512
    }
}

@Composable
private fun AlienScreamerApp() {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxSystemVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    
    val engine = remember { SynthEngine() }
    var vcoFrequency by remember { mutableFloatStateOf(220f) }
    var lfoDepth by remember { mutableFloatStateOf(80f) }
    var lfoFrequency by remember { mutableFloatStateOf(4f) }
    var hardClippingEnabled by remember { mutableStateOf(false) }
    var volume by remember { 
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxSystemVolume.coerceAtLeast(1)
        ) 
    }
    var waveShape by remember { mutableStateOf(WaveShape.SINE) }
    var waveform by remember { mutableStateOf(FloatArray(512)) }
    var visualizerMode by remember { mutableStateOf(VisualizerMode.OSCILLOSCOPE) }

    DisposableEffect(Unit) {
        engine.start()
        onDispose {
            engine.stop()
        }
    }

    LaunchedEffect(vcoFrequency, lfoDepth, lfoFrequency, hardClippingEnabled, volume, waveShape) {
        engine.vcoFrequencyHz = vcoFrequency
        engine.lfoDepthHz = lfoDepth
        engine.lfoFrequencyHz = lfoFrequency
        engine.hardClippingEnabled = hardClippingEnabled
        engine.volume = volume
        engine.waveShape = waveShape
    }

    LaunchedEffect(engine) {
        while (isActive) {
            waveform = engine.snapshotWaveform()
            delay(33)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07110C))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_alien_head),
                contentDescription = "Logo",
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "Alien Screamer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFC8FFE2)
            )
        }

        Visualizer(
            waveform = waveform,
            mode = visualizerMode,
            onModeChange = { visualizerMode = it }
        )

        WaveShapeSelector(
            selected = waveShape,
            onShapeSelected = { waveShape = it }
        )

        SynthSlider(
            label = "VCO Frequency",
            value = vcoFrequency,
            range = 50f..1500f,
            unit = "Hz",
            onValueChange = { vcoFrequency = it }
        )

        SynthSlider(
            label = "LFO Modulation",
            value = lfoDepth,
            range = 0f..500f,
            unit = "Hz depth",
            onValueChange = { lfoDepth = it }
        )

        SynthSlider(
            label = "LFO Frequency",
            value = lfoFrequency,
            range = 1f..20f,
            unit = "Hz",
            onValueChange = { lfoFrequency = it }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF102019), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Output Processing",
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFFE3FFF0)
            )

            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Master Volume",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE3FFF0)
                    )
                    Text(
                        text = "${(volume * 100f).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9DD9B5)
                    )
                }
                Slider(
                    value = volume,
                    onValueChange = { 
                        volume = it
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            (it * maxSystemVolume).roundToInt(),
                            0
                        )
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.height(32.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hard Clipping",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE3FFF0)
                    )
                    Text(
                        text = "Aggressive digital clip",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9DD9B5)
                    )
                }
                Switch(
                    checked = hardClippingEnabled,
                    onCheckedChange = { hardClippingEnabled = it }
                )
            }
        }
    }
}

@Composable
private fun Visualizer(
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
private fun OscilloscopeCanvas(waveform: FloatArray) {
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

        if (waveform.isEmpty()) return@Canvas

        val pathPoints = waveform.mapIndexed { index, sample ->
            val x = index.toFloat() / (waveform.size - 1).coerceAtLeast(1) * size.width
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
private fun FftCanvas(waveform: FloatArray) {
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

private fun computeFFT(samples: FloatArray): FloatArray {
    val n = samples.size
    val real = samples.copyOf()
    val imag = FloatArray(n)

    var j = 0
    for (i in 0 until n) {
        if (i < j) {
            val temp = real[i]; real[i] = real[j]; real[j] = temp
        }
        var m = n shr 1
        while (m >= 1 && j >= m) {
            j -= m
            m = m shr 1
        }
        j += m
    }

    var len = 2
    while (len <= n) {
        val ang = -2.0 * PI / len
        val wlenReal = cos(ang).toFloat()
        val wlenImag = sin(ang).toFloat()
        for (i in 0 until n step len) {
            var wReal = 1f
            var wImag = 0f
            for (k in 0 until len / 2) {
                val uReal = real[i + k]
                val uImag = imag[i + k]
                val vReal = real[i + k + len / 2] * wReal - imag[i + k + len / 2] * wImag
                val vImag = real[i + k + len / 2] * wImag + imag[i + k + len / 2] * wReal
                real[i + k] = uReal + vReal
                imag[i + k] = uImag + vImag
                real[i + k + len / 2] = uReal - vReal
                imag[i + k + len / 2] = uImag - vImag
                val nextWReal = wReal * wlenReal - wImag * wlenImag
                wImag = wReal * wlenImag + wImag * wlenReal
                wReal = nextWReal
            }
        }
        len = len shl 1
    }

    val magnitudes = FloatArray(n / 2)
    for (i in 0 until n / 2) {
        magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i]) / (n / 2)
    }
    return magnitudes
}

@Composable
private fun WaveShapeSelector(
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
private fun SynthSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF102019), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFFE3FFF0)
            )
            Text(
                text = "${value.roundToInt()} $unit",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9DD9B5)
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.height(32.dp)
        )
    }
}
