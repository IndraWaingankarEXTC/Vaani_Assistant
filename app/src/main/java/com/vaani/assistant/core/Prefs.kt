package com.vaani.assistant.core

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!::sp.isInitialized) {
            sp = ctx.applicationContext.getSharedPreferences("vaani", Context.MODE_PRIVATE)
        }
    }

    private fun ensure() {
        check(::sp.isInitialized) { "Prefs.init() not called" }
    }

    /** Keep the foreground service alive across reboots and app kills. */
    var serviceEnabled: Boolean
        get() = ensure().let { sp.getBoolean("service_enabled", false) }
        set(v) = sp.edit().putBoolean("service_enabled", v).apply()

    /** Floating mic bubble shown over other apps. */
    var bubbleEnabled: Boolean
        get() = sp.getBoolean("bubble_enabled", false)
        set(v) = sp.edit().putBoolean("bubble_enabled", v).apply()

    /** Optional on-device LLM planner for utterances the rule grammar misses. */
    var llmEnabled: Boolean
        get() = sp.getBoolean("llm_enabled", false)
        set(v) = sp.edit().putBoolean("llm_enabled", v).apply()

    var llmModelPath: String?
        get() = sp.getString("llm_model_path", null)
        set(v) = sp.edit().putString("llm_model_path", v).apply()

    /** Speak confirmations and errors out loud. */
    var speakReplies: Boolean
        get() = sp.getBoolean("speak_replies", true)
        set(v) = sp.edit().putBoolean("speak_replies", v).apply()

    /** 0.0 - 1.0 feedback volume for the assistant's own voice. */
    var speechRate: Float
        get() = sp.getFloat("speech_rate", 1.0f)
        set(v) = sp.edit().putFloat("speech_rate", v).apply()

    /**
     * Set just before an authorised commit is tapped, cleared just after. If it is still here on
     * the next start, the phone died mid-press and the user needs to check.
     */
    var pendingCommit: String?
        get() = sp.getString("pending_commit", null)
        set(v) = sp.edit().putString("pending_commit", v).apply()

    /** Which of the built-in voice profiles answers. See voice/VoiceProfiles.kt. */
    var voiceProfile: Int
        get() = sp.getInt("voice_profile", 0)
        set(v) = sp.edit().putInt("voice_profile", v).apply()

    /** Add punctuation and capitalisation to dictated sentences. */
    var autoPunctuation: Boolean
        get() = sp.getBoolean("auto_punctuation", true)
        set(v) = sp.edit().putBoolean("auto_punctuation", v).apply()

    /** Leave dictation mode after this much silence. */
    var dictationIdleMs: Long
        get() = sp.getLong("dictation_idle_ms", 60_000L)
        set(v) = sp.edit().putLong("dictation_idle_ms", v).apply()

    /**
     * Stop at checkout, show the order summary, and wait for a human yes.
     * Off means Vaani refuses order steps outright instead of gating them.
     */
    var confirmOrders: Boolean
        get() = sp.getBoolean("confirm_orders", true)
        set(v) = sp.edit().putBoolean("confirm_orders", v).apply()

    /** Hands-free: listen for the wake phrase all day. */
    var wakeWordEnabled: Boolean
        get() = sp.getBoolean("wake_enabled", true)
        set(v) = sp.edit().putBoolean("wake_enabled", v).apply()

    /** The phrase that wakes it. Matched fuzzily, in any script. */
    var wakePhrase: String
        get() = sp.getString("wake_phrase", "vaani") ?: "vaani"
        set(v) = sp.edit().putString("wake_phrase", v).apply()

    /** After a command, keep listening for a follow-up without the wake word. */
    var followUpEnabled: Boolean
        get() = sp.getBoolean("follow_up", true)
        set(v) = sp.edit().putBoolean("follow_up", v).apply()

    var followUpMs: Long
        get() = sp.getLong("follow_up_ms", 6000L)
        set(v) = sp.edit().putLong("follow_up_ms", v).apply()

    /** Recogniser hint. en-IN is the right default for code-mixed Indian speech. */
    var asrLanguage: String
        get() = sp.getString("asr_language", "en-IN") ?: "en-IN"
        set(v) = sp.edit().putString("asr_language", v).apply()

    /** Let the detected language nudge the next recognition hint. */
    var autoLanguage: Boolean
        get() = sp.getBoolean("auto_language", true)
        set(v) = sp.edit().putBoolean("auto_language", v).apply()

    /** Keep the local model mapped between commands (needs the 3 GB budget). */
    var modelResident: Boolean
        get() = sp.getBoolean("model_resident", true)
        set(v) = sp.edit().putBoolean("model_resident", v).apply()

    /** Budget the app plans against: 2 GB physical + 1 GB reclaimable. */
    var memoryBudgetMb: Long
        get() = sp.getLong("memory_budget_mb", 3072L)
        set(v) = sp.edit().putLong("memory_budget_mb", v).apply()

    var lastRecipeImport: Long
        get() = sp.getLong("last_recipe_import", 0L)
        set(v) = sp.edit().putLong("last_recipe_import", v).apply()
}
