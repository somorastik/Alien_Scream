package com.alienscream.app.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class WaveShape {
    SINE, TRIANGLE, SAWTOOTH, SQUARE
}

enum class VisualizerMode {
    OSCILLOSCOPE, FFT
}

class SynthEngine {
    @Volatile
    var vcoFrequencyHz: Float = 220f

    @Volatile
    var lfoDepthHz: Float = 80f

    @Volatile
    var lfoDepthModAmount: Float = 0f

    @Volatile
    var lfoDepthModEnabled: Boolean = false

    @Volatile
    var lfoFrequencyHz: Float = 4f

    @Volatile
    var lfoFrequencyModAmount: Float = 0f

    @Volatile
    var lfoFrequencyModEnabled: Boolean = false

    @Volatile
    var hardClippingEnabled: Boolean = false

    @Volatile
    var hardClippingAmount: Float = 0.5f

    @Volatile
    var delayEnabled: Boolean = false

    @Volatile
    var reverbEnabled: Boolean = false

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
        var modLfoPhase = 0.0

        // Delay line: 0.5s at 44.1kHz = ~22050 samples
        val delayBuffer = FloatArray(SAMPLE_RATE / 2)
        var delayWriteIndex = 0

        // Advanced Reverb: Multiple parallel comb filters and all-pass filters (Schroeder)
        val comb1 = FloatArray(1116)
        val comb2 = FloatArray(1188)
        val comb3 = FloatArray(1277)
        val comb4 = FloatArray(1356)
        val allpass1 = FloatArray(225)
        val allpass2 = FloatArray(556)
        
        var c1Idx = 0; var c2Idx = 0; var c3Idx = 0; var c4Idx = 0
        var ap1Idx = 0; var ap2Idx = 0

        val radiansPerSample = 2.0 * PI / SAMPLE_RATE
        val modLfoFreqStep = 0.5 * radiansPerSample

        try {
            while (running) {
                // Snapshot parameters...
                val baseFreq = vcoFrequencyHz
                val baseLfoDepth = lfoDepthHz
                val baseLfoFreq = lfoFrequencyHz
                val lfoDModAmt = lfoDepthModAmount
                val lfoFModAmt = lfoFrequencyModAmount
                val lfoDModEnabled = lfoDepthModEnabled
                val lfoFModEnabled = lfoFrequencyModEnabled
                val clipEnabled = hardClippingEnabled
                val clipAmt = hardClippingAmount.toDouble()
                val dEnabled = delayEnabled
                val rEnabled = reverbEnabled
                val engineVolume = volume
                val currentWaveShape = waveShape

                val scopeBuffer = FloatArray(BUFFER_SAMPLES)
                val threshold = if (clipEnabled) 0.25 * (1.0 - clipAmt * 0.96).coerceAtLeast(0.01) else 0.25
                val compensation = if (clipEnabled) Math.sqrt(0.25 / threshold) else 1.0

                for (i in 0 until BUFFER_SAMPLES) {
                    val modLfoValue = sin(modLfoPhase)
                    val currentLfoDepth = if (lfoDModEnabled) baseLfoDepth + (modLfoValue * lfoDModAmt).toFloat() else baseLfoDepth
                    var currentLfoFrequency = if (lfoFModEnabled) baseLfoFreq + (modLfoValue * lfoFModAmt).toFloat() else baseLfoFreq
                    currentLfoFrequency = maxOf(0.1f, currentLfoFrequency)
                    val lfoValue = sin(lfoPhase) * currentLfoDepth
                    val frequency = maxOf(10f, baseFreq + lfoValue.toFloat()).toDouble()
                    
                    val rawSample = when (currentWaveShape) {
                        WaveShape.SINE -> sin(carrierPhase)
                        WaveShape.TRIANGLE -> {
                            val p = carrierPhase / (2.0 * PI)
                            if (p < 0.5) 4.0 * p - 1.0 else 3.0 - 4.0 * p
                        }
                        WaveShape.SAWTOOTH -> (carrierPhase / PI) - 1.0
                        WaveShape.SQUARE -> if (carrierPhase < PI) 1.0 else -1.0
                    }

                    var sample = rawSample * 0.25
                    if (clipEnabled) {
                        if (sample > threshold) sample = threshold else if (sample < -threshold) sample = -threshold
                        sample *= compensation
                    }

                    if (dEnabled) {
                        val readIndex = (delayWriteIndex - (SAMPLE_RATE / 3) + delayBuffer.size) % delayBuffer.size
                        val delayedSample = delayBuffer[readIndex]
                        delayBuffer[delayWriteIndex] = (sample + delayedSample * 0.5).toFloat()
                        delayWriteIndex = (delayWriteIndex + 1) % delayBuffer.size
                        sample += delayedSample.toDouble() * 0.4
                    }

                    if (rEnabled) {
                        val dry = sample
                        // Parallel Comb Filters
                        val outC1 = comb1[c1Idx].toDouble(); comb1[c1Idx] = (dry + outC1 * 0.841).toFloat(); c1Idx = (c1Idx + 1) % comb1.size
                        val outC2 = comb2[c2Idx].toDouble(); comb2[c2Idx] = (dry + outC2 * 0.815).toFloat(); c2Idx = (c2Idx + 1) % comb2.size
                        val outC3 = comb3[c3Idx].toDouble(); comb3[c3Idx] = (dry + outC3 * 0.796).toFloat(); c3Idx = (c3Idx + 1) % comb3.size
                        val outC4 = comb4[c4Idx].toDouble(); comb4[c4Idx] = (dry + outC4 * 0.771).toFloat(); c4Idx = (c4Idx + 1) % comb4.size
                        
                        var verb = (outC1 + outC2 + outC3 + outC4) * 0.25
                        
                        // All-pass filters for diffusion
                        val ap1In = verb
                        verb = -0.5 * ap1In + allpass1[ap1Idx]
                        allpass1[ap1Idx] = (ap1In + 0.5 * verb).toFloat()
                        ap1Idx = (ap1Idx + 1) % allpass1.size
                        
                        val ap2In = verb
                        verb = -0.5 * ap2In + allpass2[ap2Idx]
                        allpass2[ap2Idx] = (ap2In + 0.5 * verb).toFloat()
                        ap2Idx = (ap2Idx + 1) % allpass2.size
                        
                        sample = dry * 0.7 + verb * 0.3
                    }

                    val finalSample = (sample * engineVolume).toFloat()
                    scopeBuffer[i] = finalSample
                    pcmBuffer[i] = (finalSample * Short.MAX_VALUE).toInt().toShort()

                    carrierPhase = (carrierPhase + frequency * radiansPerSample) % (2.0 * PI)
                    lfoPhase = (lfoPhase + currentLfoFrequency * radiansPerSample) % (2.0 * PI)
                    modLfoPhase = (modLfoPhase + modLfoFreqStep) % (2.0 * PI)
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

    companion object {
        const val SAMPLE_RATE = 44100
        const val BUFFER_SAMPLES = 512
    }
}

fun computeFFT(samples: FloatArray): FloatArray {
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
