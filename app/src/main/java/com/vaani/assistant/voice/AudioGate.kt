package com.vaani.assistant.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.vaani.assistant.core.Vlog
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Cheap always-on listen gate: 16 kHz mono PCM read in 20 ms frames, an adaptive noise floor and
 * a two-frame trigger. It costs a few percent of one core and, crucially, lets the expensive
 * speech recogniser stay asleep until someone actually talks.
 *
 * The gate releases the microphone the moment speech is detected — Android gives the mic to one
 * client at a time, so holding it while the recogniser runs would break recognition.
 */
class AudioGate(
    private val onSpeechStart: () -> Unit,
    private val onSpeechEnd: () -> Unit
) {

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val FRAME = 320           // 20 ms
        private const val TRIGGER_FRAMES = 2
        private const val HANGOVER_FRAMES = 30  // ~600 ms of quiet before we call it done
    }

    @Volatile private var running = false
    @Volatile private var speaking = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null

    val isSpeaking: Boolean get() = speaking

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        speaking = false
        thread = Thread({ loop() }, "vaani-gate").apply {
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }

    fun stop() {
        running = false
        thread?.let { runCatching { it.join(200) } }
        thread = null
        release()
    }

    private fun release() {
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
    }

    @SuppressLint("MissingPermission")
    private fun loop() {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            Vlog.e("Gate", "no usable input buffer ($minBuffer)")
            running = false
            return
        }
        val bufferSize = max(minBuffer, FRAME * 2 * 8)
        val rec = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }.getOrElse {
            Vlog.e("Gate", "AudioRecord failed: ${it.message}")
            running = false
            return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Vlog.e("Gate", "AudioRecord not initialised")
            rec.release()
            running = false
            return
        }
        record = rec
        runCatching { rec.startRecording() }.onFailure {
            Vlog.e("Gate", "startRecording failed: ${it.message}")
            running = false
            release()
            return
        }

        val frame = ShortArray(FRAME)
        var noiseFloor = 320.0
        var above = 0
        var below = 0

        while (running) {
            val read = rec.read(frame, 0, FRAME)
            if (read <= 0) continue

            var sum = 0.0
            for (i in 0 until read) {
                val v = frame[i].toDouble()
                sum += v * v
            }
            val rms = sqrt(sum / read)

            if (!speaking) noiseFloor = noiseFloor * 0.98 + rms * 0.02
            val threshold = max(noiseFloor * 3.5, 450.0)

            if (rms > threshold) {
                above++
                below = 0
            } else {
                below++
                above = 0
            }

            if (!speaking && above >= TRIGGER_FRAMES) {
                speaking = true
                runCatching { onSpeechStart() }
                break               // hand the microphone over to the recogniser and exit
            }
        }

        release()
        running = false
        speaking = false
    }
}
