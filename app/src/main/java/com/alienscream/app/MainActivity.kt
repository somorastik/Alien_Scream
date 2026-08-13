package com.alienscream.app

import android.app.Activity
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.alienscream.app.engine.SynthEngine
import com.alienscream.app.engine.VisualizerMode
import com.alienscream.app.engine.WaveShape
import com.alienscream.app.ui.SynthSlider
import com.alienscream.app.ui.Visualizer
import com.alienscream.app.ui.WaveShapeSelector
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
                    AlienScreamApp()
                }
            }
        }
    }
}

@Composable
private fun AlienScreamApp() {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxSystemVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    
    val settingsManager = remember { SettingsManager(context) }
    val initialConfig = remember { settingsManager.loadConfig() }

    val engine = remember { SynthEngine() }
    var vcoFrequency by remember { mutableFloatStateOf(initialConfig.vcoFrequency) }
    var lfoDepth by remember { mutableFloatStateOf(initialConfig.lfoDepth) }
    var lfoDepthModAmount by remember { mutableFloatStateOf(initialConfig.lfoDepthModAmount) }
    var lfoDepthModEnabled by remember { mutableStateOf(initialConfig.lfoDepthModEnabled) }

    var lfoFrequency by remember { mutableFloatStateOf(initialConfig.lfoFrequency) }
    var lfoFrequencyModAmount by remember { mutableFloatStateOf(initialConfig.lfoFrequencyModAmount) }
    var lfoFrequencyModEnabled by remember { mutableStateOf(initialConfig.lfoFrequencyModEnabled) }
    var hardClippingEnabled by remember { mutableStateOf(initialConfig.hardClippingEnabled) }
    var delayEnabled by remember { mutableStateOf(initialConfig.delayEnabled) }
    var reverbEnabled by remember { mutableStateOf(initialConfig.reverbEnabled) }
    var hardClippingAmount by remember { mutableFloatStateOf(initialConfig.hardClippingAmount) }
    var volume by remember { mutableFloatStateOf(initialConfig.volume) }
    var waveShape by remember { mutableStateOf(initialConfig.waveShape) }
    var waveform by remember { mutableStateOf(FloatArray(512)) }
    var visualizerMode by remember { mutableStateOf(initialConfig.visualizerMode) }

    DisposableEffect(Unit) {
        engine.start()
        onDispose {
            engine.stop()
        }
    }

    LaunchedEffect(vcoFrequency, lfoDepth, lfoDepthModAmount, lfoDepthModEnabled, lfoFrequency, lfoFrequencyModAmount, lfoFrequencyModEnabled, hardClippingEnabled, hardClippingAmount, delayEnabled, reverbEnabled, volume, waveShape, visualizerMode) {
        engine.vcoFrequencyHz = vcoFrequency
        engine.lfoDepthHz = lfoDepth
        engine.lfoDepthModAmount = lfoDepthModAmount
        engine.lfoDepthModEnabled = lfoDepthModEnabled
        engine.lfoFrequencyHz = lfoFrequency
        engine.lfoFrequencyModAmount = lfoFrequencyModAmount
        engine.lfoFrequencyModEnabled = lfoFrequencyModEnabled
        engine.hardClippingEnabled = hardClippingEnabled
        engine.hardClippingAmount = hardClippingAmount
        engine.delayEnabled = delayEnabled
        engine.reverbEnabled = reverbEnabled
        engine.volume = volume
        engine.waveShape = waveShape

        settingsManager.saveConfig(
            vcoFrequency, lfoDepth, lfoDepthModAmount, lfoDepthModEnabled,
            lfoFrequency, lfoFrequencyModAmount, lfoFrequencyModEnabled,
            hardClippingEnabled, delayEnabled, reverbEnabled, hardClippingAmount,
            volume, waveShape, visualizerMode
        )
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
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_alien_head),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF07110C), RoundedCornerShape(8.dp))
            )
            Text(
                text = "Alien Scream",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFC8FFE2)
            )
            IconButton(
                onClick = { (context as? Activity)?.finish() },
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF07110C), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit",
                    tint = Color(0xFFC8FFE2),
                    modifier = Modifier.size(32.dp)
                )
            }
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
            hardClippingAmount = hardClippingAmount,
            delayEnabled = delayEnabled,
            reverbEnabled = reverbEnabled,
            onVolumeChange = { 
                volume = it
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (it * maxSystemVolume).roundToInt(),
                    0
                )
            },
            onHardClippingChange = { hardClippingEnabled = it },
            onHardClippingAmountChange = { hardClippingAmount = it },
            onDelayChange = { delayEnabled = it },
            onReverbChange = { reverbEnabled = it }
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
    hardClippingAmount: Float,
    delayEnabled: Boolean,
    reverbEnabled: Boolean,
    onVolumeChange: (Float) -> Unit,
    onHardClippingChange: (Boolean) -> Unit,
    onHardClippingAmountChange: (Float) -> Unit,
    onDelayChange: (Boolean) -> Unit,
    onReverbChange: (Boolean) -> Unit
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
                modifier = Modifier.height(24.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Distortion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE3FFF0)
                )
                Text(
                    text = "Hard diode-style clip",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9DD9B5)
                )
            }
            Switch(
                checked = hardClippingEnabled,
                onCheckedChange = onHardClippingChange,
                modifier = Modifier.scale(0.8f)
            )
        }

        if (hardClippingEnabled) {
            SynthSlider(
                label = "Clipping Depth",
                value = hardClippingAmount * 100f,
                range = 0f..100f,
                unit = "%",
                onValueChange = { onHardClippingAmountChange(it / 100f) },
                compact = true
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EffectToggle(
                label = "Delay",
                enabled = delayEnabled,
                onToggle = onDelayChange,
                modifier = Modifier.weight(1f)
            )
            EffectToggle(
                label = "Reverb",
                enabled = reverbEnabled,
                onToggle = onReverbChange,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EffectToggle(
    label: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color(0xFF0D1913), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) Color(0xFF7CFFD0) else Color(0xFF9DD9B5)
        )
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.scale(0.7f)
        )
    }
}
