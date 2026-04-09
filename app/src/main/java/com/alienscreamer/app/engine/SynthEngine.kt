package com.alienscreamer.app.engine

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

        try {
            while (running) {
                val scopeBuffer = FloatArray(BUFFER_SAMPLES)
                for (i in 0 until BUFFER_SAMPLES) {
                    val modLfoValue = sin(modLfoPhase) // -1.0 to 1.0

                    var currentLfoDepth = lfoDepthHz
                    if (lfoDepthModEnabled) {
                        currentLfoDepth += (modLfoValue * lfoDepthModAmount).toFloat()
                    }

                    var currentLfoFrequency = lfoFrequencyHz
                    if (lfoFrequencyModEnabled) {
                        currentLfoFrequency += (modLfoValue * lfoFrequencyModAmount).toFloat()
                    }
                    currentLfoFrequency = maxOf(0.1f, currentLfoFrequency)

                    val lfoValue = sin(lfoPhase) * currentLfoDepth
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
                    lfoPhase = wrapPhase(lfoPhase + (2.0 * PI * currentLfoFrequency / SAMPLE_RATE))
                    modLfoPhase = wrapPhase(modLfoPhase + (2.0 * PI * 0.5 / SAMPLE_RATE)) // Fixed 0.5Hz for secondary LFO
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
