package com.vaani.assistant.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.vaani.assistant.core.Prefs
import com.vaani.assistant.core.Text
import com.vaani.assistant.core.Vlog

/**
 * Hands-free front end: an always-on energy gate, a wake phrase matched fuzzily, a follow-up
 * window so you do not have to say the name twice, and barge-in so you can talk over the reply.
 *
 * The wake word is matched on the recogniser's own transcript rather than by a dedicated keyword
 * spotter, which means no extra model and no extra library — and it works in any language whose
 * name you put in the phrase ("vaani", "वाणी", "suno vaani", or your own).
 */
class WakeWordEngine(
    private val ctx: Context,
    private val onCommand: (String) -> Unit,
    private val onBargeIn: () -> Unit,
    private val isFollowUp: () -> Boolean,
    private val isBusy: () -> Boolean
) {

    companion object {
        private const val WAKE_WINDOW_MS = 5_000L
        private val LEAD_FILLERS = listOf("hey", "ok", "okay", "arre", "are", "o", "a", "suno", "sun", "hi", "hello")
    }

    private val main = Handler(Looper.getMainLooper())
    private val gate = AudioGate(onSpeechStart = ::speechStarted, onSpeechEnd = {})
    private var asr: AsrEngine? = null

    @Volatile private var listening = false
    @Volatile private var armedUntil = 0L
    private var heardWake = false

    // ------------------------------------------------------------------ lifecycle

    fun start() {
        if (!Prefs.wakeWordEnabled) return
        main.post { gate.start() }
        Vlog.i("Wake", "listening for '${Prefs.wakePhrase}'")
    }

    fun stop() {
        main.post {
            gate.stop()
            cancelAsr()
            listening = false
        }
    }

    fun restart() {
        main.post {
            cancelAsr()
            resumeGate()
        }
    }

    /** Push-to-talk: skip the gate and the wake word entirely. */
    fun listenNow() {
        main.post {
            gate.stop()
            heardWake = true
            beginAsr()
        }
    }

    /** Open a window where a command is accepted without the wake word. */
    fun armFollowUp(ms: Long = WAKE_WINDOW_MS) {
        armedUntil = System.currentTimeMillis() + ms
    }

    val isListening: Boolean get() = listening

    // ------------------------------------------------------------------ gate -> asr

    private fun speechStarted() {
        main.post {
            if (isBusy()) {
                // A plan is running (or the reply is still playing): stop talking and stay quiet
                // rather than transcribing our own voice back into a command.
                onBargeIn()
                resumeGate()
            } else {
                beginAsr()
            }
        }
    }

    private fun beginAsr() {
        if (listening) return
        listening = true
        heardWake = false
        val engine = AsrEngine(ctx, object : AsrEngine.Callbacks {
            override fun onPartial(text: String) {
                if (!heardWake && wakeIn(text) != null) {
                    heardWake = true
                    Vlog.i("Wake", "wake heard in partial: $text")
                }
            }

            override fun onFinal(text: String) {
                listening = false
                asr = null
                handleTranscript(text)
            }

            override fun onError(message: String) {
                listening = false
                asr = null
                Vlog.i("Wake", message)
                resumeGate()
            }
        })
        engine.languageTag = Prefs.asrLanguage
        asr = engine
        engine.start()
    }

    private fun cancelAsr() {
        asr?.cancel()
        asr = null
        listening = false
    }

    /**
     * Bring the gate back once the service is idle. Retried a few times because the reply may
     * still be playing; after ~5 s we listen anyway rather than stay deaf.
     */
    private fun resumeGate(attempt: Int = 0) {
        if (!Prefs.wakeWordEnabled) return
        main.postDelayed({
            if (listening) return@postDelayed
            val busyNow = isBusy()
            when {
                !busyNow -> gate.start()
                attempt < 12 -> resumeGate(attempt + 1)
                else -> gate.start()
            }
        }, 400)
    }

    // ------------------------------------------------------------------ dispatch

    private fun handleTranscript(text: String) {
        if (text.isBlank()) {
            resumeGate()
            return
        }
        val followUp = isFollowUp() || System.currentTimeMillis() < armedUntil
        val remainder = wakeIn(text)

        when {
            remainder != null && remainder.isNotBlank() -> {
                Vlog.i("Wake", "command after wake: $remainder")
                armedUntil = 0
                onCommand(remainder)     // VoiceService restarts the gate when the plan is done
            }
            remainder != null -> {
                // Just the name: answer and stay open for the actual command.
                Vlog.i("Wake", "wake only, arming follow-up")
                armFollowUp()
                onCommand("")          // VoiceService turns this into a short "haan?"
            }
            followUp -> {
                Vlog.i("Wake", "follow-up command: $text")
                armedUntil = 0
                onCommand(text)
            }
            else -> {
                Vlog.i("Wake", "ignored (no wake word): $text")
                resumeGate()
            }
        }
    }

    /** Returns the text after the wake phrase, "" if only the phrase was said, null if absent. */
    fun wakeIn(text: String): String? {
        val phrase = Prefs.wakePhrase
        val tokens = Text.norm(text).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        val wakeTokens = Text.norm(phrase).split(' ').filter { it.isNotBlank() }
        val targets = (wakeTokens + listOf("vaani", "vani", "wani", "vaanee", "wanni")).distinct()

        var matchedAt = -1
        val limit = minOf(tokens.size, 5)
        for (i in 0 until limit) {
            val token = tokens[i]
            if (targets.any { wake ->
                    token == wake ||
                        (token.first() == wake.first() && token.length == wake.length &&
                            Text.distance(token, wake) <= 1)
                }) {
                matchedAt = i
                break
            }
        }
        if (matchedAt < 0) return null

        // Drop the wake word and any lead-in fillers around it.
        val before = tokens.take(matchedAt).filterNot { it in LEAD_FILLERS }
        val after = tokens.drop(matchedAt + 1).filterNot { it in LEAD_FILLERS }
        val rest = (before + after).joinToString(" ").trim()
        return rest
    }
}
