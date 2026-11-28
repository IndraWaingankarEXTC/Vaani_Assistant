package com.vaani.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.vaani.assistant.core.Vlog

/**
 * Speech to text with a strict preference for the on-device recogniser, so no audio leaves the
 * phone when the device supports it. The recogniser lives in its own process, so it does not
 * count against Vaani's own memory budget.
 */
class AsrEngine(private val ctx: Context, private val callbacks: Callbacks) {

    interface Callbacks {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
    }

    /** BCP-47 tag the recogniser should hint. en-IN transcribes Hindi/Tamil words in Latin
     *  script, which is exactly what code-mixed speech needs. */
    var languageTag: String = "en-IN"

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false

    /**
     * Forcing the on-device recogniser is right for privacy but wrong when the phone has not
     * downloaded the language pack — recognition then fails every single time, which is what
     * "voice rarely works" looks like. So: prefer on-device, and drop the preference the moment it
     * reports a network/offline failure, then try once more.
     */
    private var preferOffline = true
    private var retriedOnline = false
    private val hardStop = Runnable { stopListening() }

    fun start() {
        main.post {
            if (active) return@post
            active = true
            destroy()
            val engine = create()
            if (engine == null) {
                active = false
                callbacks.onError("speech recognition unavailable")
                return@post
            }
            recognizer = engine
            engine.setRecognitionListener(listener)
            runCatching { engine.startListening(request()) }.onFailure {
                Vlog.e("ASR", "start failed: ${it.message}")
                active = false
                callbacks.onError("could not start listening")
            }
            main.postDelayed(hardStop, 15_000)
        }
    }

    /** Ask for what was heard so far. Triggers onResults with the best partial. */
    fun stopListening() {
        main.post {
            if (!active) return@post
            runCatching { recognizer?.stopListening() }
        }
    }

    fun cancel() {
        main.post {
            active = false
            retriedOnline = false
            main.removeCallbacks(hardStop)
            destroy()
        }
    }

    private fun create(): SpeechRecognizer? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        } else {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        }
    }.getOrElse {
        Vlog.e("ASR", "recogniser unavailable: ${it.message}")
        null
    }

    private fun request(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        // Generous silence windows: the old 1.4 s cut people off mid-sentence, which read as
        // "it doesn't hear me".
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200L)
    }

    private fun destroy() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { callbacks.onPartial(it) }
        }

        override fun onResults(results: Bundle?) {
            main.removeCallbacks(hardStop)
            active = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            destroy()
            if (text.isNullOrBlank()) callbacks.onError("I didn't hear anything")
            else callbacks.onFinal(text)
        }

        override fun onError(error: Int) {
            main.removeCallbacks(hardStop)
            active = false
            destroy()
            // If the on-device model is missing, retry once through the network.
            if (preferOffline && !retriedOnline && (
                    error == SpeechRecognizer.ERROR_NETWORK ||
                        error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_CLIENT ||
                        error == SpeechRecognizer.ERROR_SERVER
                    )
            ) {
                preferOffline = false
                retriedOnline = true
                Vlog.i("ASR", "on-device failed ($error) — retrying with the network")
                start()
                return
            }

            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "microphone permission is missing"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "the speech service is offline and needs the network"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "the recogniser is busy"
                else -> "speech error $error"
            }
            callbacks.onError(message)
        }
    }
}
