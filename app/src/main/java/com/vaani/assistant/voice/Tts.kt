package com.vaani.assistant.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.vaani.assistant.core.Prefs
import com.vaani.assistant.core.Vlog
import java.util.Locale

/** Feedback voice. Uses the phone's own TTS engine — no download, no model in our memory. */
class Tts(private val ctx: Context) {

    private var engine: TextToSpeech? = null
    private var ready = false
    private var counter = 0

    /** True while the reply is audible, so the gate can implement barge-in. */
    @Volatile
    var isSpeaking: Boolean = false
        private set

    fun init() {
        if (engine != null) return
        engine = TextToSpeech(ctx) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                engine?.language = Locale("en", "IN")
                engine?.setSpeechRate(Prefs.speechRate)
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                    }

                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                    }
                })
            } else {
                Vlog.e("TTS", "engine init failed")
            }
        }
    }

    /** Speak in the language tag that matches what the user just said. */
    fun speak(text: String, languageTag: String = "en-IN") {
        val engine = engine ?: run {
            Vlog.i("TTS", "not ready, dropping: $text")
            return
        }
        if (!Prefs.speakReplies) {
            Vlog.i("TTS", text)
            return
        }
        // The profile decides who is speaking; the caller decides what language.
        val profile = VoiceProfiles.of(Prefs.voiceProfile)
        runCatching {
            engine.setPitch(profile.pitch)
            engine.setSpeechRate(Prefs.speechRate * profile.rate)
            engine.language = Locale.forLanguageTag(
                if (languageTag.isBlank()) profile.languageTag else languageTag
            )
        }
        counter += 1
        runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vaani-$counter")
        }.onFailure { Vlog.e("TTS", "speak failed: ${it.message}") }
    }

    fun stop() {
        isSpeaking = false
        runCatching { engine?.stop() }
    }

    fun shutdown() {
        isSpeaking = false
        runCatching { engine?.shutdown() }
        engine = null
        ready = false
    }
}
