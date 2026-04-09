import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.GridLayout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh

private class VcoFrequencyModule(initialHz: Double) {
    private val frequencyHz = AtomicReference(initialHz)

    fun setFrequencyHz(value: Double) {
        frequencyHz.set(value)
    }

    fun getFrequencyHz(): Double = frequencyHz.get()
}

private class LfoFrequencyModule(initialHz: Double) {
    private val frequencyHz = AtomicReference(initialHz)

    fun setFrequencyHz(value: Double) {
        frequencyHz.set(value)
    }

    fun getFrequencyHz(): Double = frequencyHz.get()
}

private class LfoModulationModule(initialDepthHz: Double) {
    private val depthHz = AtomicReference(initialDepthHz)

    fun setDepthHz(value: Double) {
        depthHz.set(value)
    }

    fun getDepthHz(): Double = depthHz.get()
}

private class DistortionModule(initialEnabled: Boolean) {
    private val enabled = AtomicBoolean(initialEnabled)

    fun setEnabled(value: Boolean) {
        enabled.set(value)
    }

    fun isEnabled(): Boolean = enabled.get()
}

private class SynthEngine(
    private val vcoFrequency: VcoFrequencyModule,
    private val lfoFrequency: LfoFrequencyModule,
    private val lfoModulation: LfoModulationModule,
    private val distortion: DistortionModule
) {
    private val running = AtomicBoolean(false)
    private var audioThread: Thread? = null
    private val sampleRate = 44_100.0
    private val scopeSamples = 512
    private val waveformSnapshot = AtomicReference(DoubleArray(scopeSamples))

    fun start() {
        if (!running.compareAndSet(false, true)) return

        audioThread = Thread({
            runAudio()
        }, "alien-screamer-audio").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        audioThread?.join(500)
    }

    fun getWaveformSnapshot(): DoubleArray = waveformSnapshot.get().copyOf()

    private fun runAudio() {
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        val info = DataLine.Info(SourceDataLine::class.java, format)
        val line = AudioSystem.getLine(info) as SourceDataLine
        val bufferSamples = 512
        val buffer = ByteArray(bufferSamples * 2)
        var carrierPhase = 0.0
        var lfoPhase = 0.0

        line.open(format, buffer.size * 4)
        line.start()

        try {
            while (running.get()) {
                var index = 0
                val scopeBuffer = DoubleArray(bufferSamples)
                repeat(bufferSamples) {
                    val lfoHz = lfoFrequency.getFrequencyHz()
                    val lfoDepthHz = lfoModulation.getDepthHz()
                    val baseHz = vcoFrequency.getFrequencyHz()
                    val modulation = sin(lfoPhase) * lfoDepthHz
                    val instantaneousHz = (baseHz + modulation).coerceAtLeast(10.0)
                    var sampleValue = sin(carrierPhase) * 0.25
                    if (distortion.isEnabled()) {
                        sampleValue = tanh(sampleValue * 6.0) * 0.35
                    }
                    val pcm = (sampleValue * Short.MAX_VALUE).toInt().toShort()
                    scopeBuffer[it] = sampleValue

                    buffer[index++] = (pcm.toInt() and 0xff).toByte()
                    buffer[index++] = ((pcm.toInt() shr 8) and 0xff).toByte()

                    carrierPhase = wrapPhase(carrierPhase + (2.0 * PI * instantaneousHz / sampleRate))
                    lfoPhase = wrapPhase(lfoPhase + (2.0 * PI * lfoHz / sampleRate))
                }

                waveformSnapshot.set(scopeBuffer)
                line.write(buffer, 0, buffer.size)
            }
        } finally {
            line.drain()
            line.stop()
            line.close()
        }
    }

    private fun wrapPhase(value: Double): Double {
        return if (value >= 2.0 * PI) value - 2.0 * PI else value
    }
}

private class OscilloscopePanel(private val engine: SynthEngine) : JPanel() {
    init {
        preferredSize = Dimension(396, 140)
        background = Color(12, 18, 16)
        border = BorderFactory.createTitledBorder("Oscilloscope")

        Timer(33) { repaint() }.start()
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)

        val g = graphics.create()
        val width = width
        val height = height
        val midY = height / 2
        val samples = engine.getWaveformSnapshot()
        if (samples.isEmpty()) {
            g.dispose()
            return
        }

        g.color = Color(32, 64, 52)
        repeat(4) { step ->
            val y = (height * (step + 1)) / 5
            g.drawLine(0, y, width, y)
        }
        g.drawLine(0, midY, width, midY)

        g.color = Color(119, 255, 188)
        var lastX = 0
        var lastY = sampleToY(samples[0], height)
        for (i in 1 until samples.size) {
            val x = i * (width - 1) / (samples.size - 1)
            val y = sampleToY(samples[i], height)
            g.drawLine(lastX, lastY, x, y)
            lastX = x
            lastY = y
        }

        g.dispose()
    }

    private fun sampleToY(sample: Double, height: Int): Int {
        val normalized = sample.coerceIn(-1.0, 1.0)
        return (((1.0 - ((normalized + 1.0) / 2.0)) * (height - 1))).toInt()
    }
}

private fun createSliderPanel(
    title: String,
    min: Int,
    max: Int,
    initial: Int,
    formatter: (Int) -> String,
    onChange: (Int) -> Unit
): JPanel {
    val valueLabel = JLabel(formatter(initial), SwingConstants.CENTER)
    val slider = JSlider(min, max, initial)
    slider.majorTickSpacing = (max - min) / 4
    slider.minorTickSpacing = maxOf(1, (max - min) / 16)
    slider.paintTicks = true
    slider.paintLabels = true
    slider.addChangeListener {
        val value = slider.value
        valueLabel.text = formatter(value)
        onChange(value)
    }

    return JPanel(BorderLayout(8, 8)).apply {
        border = BorderFactory.createTitledBorder(title)
        add(valueLabel, BorderLayout.NORTH)
        add(slider, BorderLayout.CENTER)
    }
}

private fun createUi(
    engine: SynthEngine,
    vco: VcoFrequencyModule,
    lfoMod: LfoModulationModule,
    lfoFreq: LfoFrequencyModule,
    distortion: DistortionModule
) {
    val frame = JFrame("Alien Screamer")
    frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE

    val controls = JPanel(GridLayout(3, 1, 12, 12)).apply {
        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        add(
            createSliderPanel(
                title = "VCO Frequency",
                min = 50,
                max = 1_500,
                initial = vco.getFrequencyHz().toInt(),
                formatter = { "$it Hz" }
            ) { vco.setFrequencyHz(it.toDouble()) }
        )
        add(
            createSliderPanel(
                title = "LFO Modulation",
                min = 0,
                max = 500,
                initial = lfoMod.getDepthHz().toInt(),
                formatter = { "$it Hz depth" }
            ) { lfoMod.setDepthHz(it.toDouble()) }
        )
        add(
            createSliderPanel(
                title = "LFO Frequency",
                min = 1,
                max = 20,
                initial = lfoFreq.getFrequencyHz().toInt(),
                formatter = { "$it Hz" }
            ) { lfoFreq.setFrequencyHz(it.toDouble()) }
        )
    }

    val distortionSwitch = JCheckBox("Distortion").apply {
        horizontalAlignment = SwingConstants.CENTER
        isSelected = distortion.isEnabled()
        addActionListener {
            distortion.setEnabled(isSelected)
        }
    }

    val centerPanel = JPanel(BorderLayout(0, 12)).apply {
        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        add(OscilloscopePanel(engine), BorderLayout.NORTH)
        add(controls, BorderLayout.CENTER)
        add(distortionSwitch, BorderLayout.SOUTH)
    }

    frame.add(centerPanel, BorderLayout.CENTER)
    frame.setSize(440, 680)
    frame.setLocationRelativeTo(null)
    frame.isVisible = true

    engine.start()
    Runtime.getRuntime().addShutdownHook(Thread { engine.stop() })
}

fun main() {
    val vcoFrequency = VcoFrequencyModule(initialHz = 220.0)
    val lfoModulation = LfoModulationModule(initialDepthHz = 80.0)
    val lfoFrequency = LfoFrequencyModule(initialHz = 4.0)
    val distortion = DistortionModule(initialEnabled = false)
    val engine = SynthEngine(vcoFrequency, lfoFrequency, lfoModulation, distortion)

    SwingUtilities.invokeLater {
        createUi(engine, vcoFrequency, lfoModulation, lfoFrequency, distortion)
    }
}
