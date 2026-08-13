package com.alienscream.app

import android.content.Context
import android.content.SharedPreferences
import com.alienscream.app.engine.VisualizerMode
import com.alienscream.app.engine.WaveShape

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("synth_prefs", Context.MODE_PRIVATE)

    fun saveConfig(
        vcoFrequency: Float,
        lfoDepth: Float,
        lfoDepthModAmount: Float,
        lfoDepthModEnabled: Boolean,
        lfoFrequency: Float,
        lfoFrequencyModAmount: Float,
        lfoFrequencyModEnabled: Boolean,
        hardClippingEnabled: Boolean,
        delayEnabled: Boolean,
        reverbEnabled: Boolean,
        hardClippingAmount: Float,
        volume: Float,
        waveShape: WaveShape,
        visualizerMode: VisualizerMode
    ) {
        prefs.edit().apply {
            putFloat("vcoFrequency", vcoFrequency)
            putFloat("lfoDepth", lfoDepth)
            putFloat("lfoDepthModAmount", lfoDepthModAmount)
            putBoolean("lfoDepthModEnabled", lfoDepthModEnabled)
            putFloat("lfoFrequency", lfoFrequency)
            putFloat("lfoFrequencyModAmount", lfoFrequencyModAmount)
            putBoolean("lfoFrequencyModEnabled", lfoFrequencyModEnabled)
            putBoolean("hardClippingEnabled", hardClippingEnabled)
            putBoolean("delayEnabled", delayEnabled)
            putBoolean("reverbEnabled", reverbEnabled)
            putFloat("hardClippingAmount", hardClippingAmount)
            putFloat("volume", volume)
            putString("waveShape", waveShape.name)
            putString("visualizerMode", visualizerMode.name)
            apply()
        }
    }

    fun loadConfig(): SynthConfig {
        return SynthConfig(
            vcoFrequency = prefs.getFloat("vcoFrequency", 220f),
            lfoDepth = prefs.getFloat("lfoDepth", 80f),
            lfoDepthModAmount = prefs.getFloat("lfoDepthModAmount", 0f),
            lfoDepthModEnabled = prefs.getBoolean("lfoDepthModEnabled", false),
            lfoFrequency = prefs.getFloat("lfoFrequency", 4f),
            lfoFrequencyModAmount = prefs.getFloat("lfoFrequencyModAmount", 0f),
            lfoFrequencyModEnabled = prefs.getBoolean("lfoFrequencyModEnabled", false),
            hardClippingEnabled = prefs.getBoolean("hardClippingEnabled", false),
            delayEnabled = prefs.getBoolean("delayEnabled", false),
            reverbEnabled = prefs.getBoolean("reverbEnabled", false),
            hardClippingAmount = prefs.getFloat("hardClippingAmount", 0.5f),
            volume = prefs.getFloat("volume", 0.7f),
            waveShape = WaveShape.valueOf(prefs.getString("waveShape", WaveShape.SINE.name) ?: WaveShape.SINE.name),
            visualizerMode = VisualizerMode.valueOf(prefs.getString("visualizerMode", VisualizerMode.OSCILLOSCOPE.name) ?: VisualizerMode.OSCILLOSCOPE.name)
        )
    }
}

data class SynthConfig(
    val vcoFrequency: Float,
    val lfoDepth: Float,
    val lfoDepthModAmount: Float,
    val lfoDepthModEnabled: Boolean,
    val lfoFrequency: Float,
    val lfoFrequencyModAmount: Float,
    val lfoFrequencyModEnabled: Boolean,
    val hardClippingEnabled: Boolean,
    val delayEnabled: Boolean,
    val reverbEnabled: Boolean,
    val hardClippingAmount: Float,
    val volume: Float,
    val waveShape: WaveShape,
    val visualizerMode: VisualizerMode
)
