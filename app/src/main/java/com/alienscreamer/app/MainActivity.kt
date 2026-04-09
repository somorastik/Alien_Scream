package com.alienscreamer.app

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alienscreamer.app.engine.SynthEngine
import com.alienscreamer.app.engine.VisualizerMode
import com.alienscreamer.app.engine.WaveShape
import com.alienscreamer.app.ui.SynthSlider
import com.alienscreamer.app.ui.Visualizer
import com.alienscreamer.app.ui.WaveShapeSelector
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

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

@Composable
private fun AlienScreamerApp() {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxSystemVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    
    val engine = remember { SynthEngine() }
    var vcoFrequency by remember { mutableFloatStateOf(220f) }
    var lfoDepth by remember { mutableFloatStateOf(80f) }
    var lfoDepthModAmount by remember { mutableFloatStateOf(0f) }
    var lfoDepthModEnabled by remember { mutableStateOf(false) }

    var lfoFrequency by remember { mutableFloatStateOf(4f) }
    var lfoFrequencyModAmount by remember { mutableFloatStateOf(0f) }
    var lfoFrequencyModEnabled by remember { mutableStateOf(false) }
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

    LaunchedEffect(vcoFrequency, lfoDepth, lfoDepthModAmount, lfoDepthModEnabled, lfoFrequency, lfoFrequencyModAmount, lfoFrequencyModEnabled, hardClippingEnabled, volume, waveShape) {
        engine.vcoFrequencyHz = vcoFrequency
        engine.lfoDepthHz = lfoDepth
        engine.lfoDepthModAmount = lfoDepthModAmount
        engine.lfoDepthModEnabled = lfoDepthModEnabled
        engine.lfoFrequencyHz = lfoFrequency
        engine.lfoFrequencyModAmount = lfoFrequencyModAmount
        engine.lfoFrequencyModEnabled = lfoFrequencyModEnabled
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_alien_head),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF07110C), RoundedCornerShape(8.dp))
            )
            Text(
                text = "Alien Screamer",
                style = MaterialTheme.typography.headlineMedium,
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

        ModulatableSliderRow(
            label = "LFO Modulation",
            value = lfoDepth,
            range = 0f..500f,
            unit = "Hz",
            onValueChange = { lfoDepth = it },
            modAmount = lfoDepthModAmount,
            modEnabled = lfoDepthModEnabled,
            onModAmountChange = { lfoDepthModAmount = it },
            onModEnabledChange = { lfoDepthModEnabled = it }
        )

        ModulatableSliderRow(
            label = "LFO Frequency",
            value = lfoFrequency,
            range = 1f..20f,
            unit = "Hz",
            onValueChange = { lfoFrequency = it },
            modAmount = lfoFrequencyModAmount,
            modEnabled = lfoFrequencyModEnabled,
            onModAmountChange = { lfoFrequencyModAmount = it },
            onModEnabledChange = { lfoFrequencyModEnabled = it }
        )

        OutputProcessingSection(
            volume = volume,
            hardClippingEnabled = hardClippingEnabled,
            onVolumeChange = { 
                volume = it
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (it * maxSystemVolume).roundToInt(),
                    0
                )
            },
            onHardClippingChange = { hardClippingEnabled = it }
        )
    }
}

@Composable
private fun ModulatableSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit,
    modAmount: Float,
    modEnabled: Boolean,
    onModAmountChange: (Float) -> Unit,
    onModEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Base Parameter (Half width approx)
        Box(modifier = Modifier.weight(1f)) {
            SynthSlider(
                label = label,
                value = value,
                range = range,
                unit = unit,
                onValueChange = onValueChange,
                compact = true
            )
        }

        // Switch in between
        Column(
            modifier = Modifier
                .height(64.dp) // Match compact SynthSlider height approx
                .background(Color(0xFF102019), RoundedCornerShape(12.dp))
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("MOD", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9DD9B5))
            Switch(
                checked = modEnabled,
                onCheckedChange = onModEnabledChange,
                modifier = Modifier.scale(0.6f)
            )
        }

        // Modulation Amount (Other half approx)
        Box(modifier = Modifier.weight(1f)) {
            SynthSlider(
                label = "Mod Amt",
                value = modAmount,
                range = 0f..range.endInclusive / 2f,
                unit = unit,
                onValueChange = onModAmountChange,
                compact = true
            )
        }
    }
}

@Composable
private fun OutputProcessingSection(
    volume: Float,
    hardClippingEnabled: Boolean,
    onVolumeChange: (Float) -> Unit,
    onHardClippingChange: (Boolean) -> Unit
) {
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
                onValueChange = onVolumeChange,
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
                onCheckedChange = onHardClippingChange
            )
        }
    }
}
