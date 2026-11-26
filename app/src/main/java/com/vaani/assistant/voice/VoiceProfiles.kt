package com.vaani.assistant.voice

/**
 * Which voice Vaani answers in.
 *
 * These are the voices the phone already has — Google's speech engine ships several per locale —
 * tuned by pitch and speed into four distinct characters. That is the honest scope: a *cloned*
 * voice needs a neural text-to-speech model on the device, which is tens of megabytes and cannot
 * be produced here. What this gives you is a choice of who is speaking, not a copy of you.
 */
object VoiceProfiles {

    data class Profile(
        val id: Int,
        val name: String,
        val languageTag: String,
        val pitch: Float,
        val rate: Float
    )

    val all: List<Profile> = listOf(
        Profile(0, "Default", "en-IN", 1.00f, 1.00f),
        Profile(1, "Voice 1 · warm", "en-IN", 0.92f, 0.97f),
        Profile(2, "Voice 2 · bright", "en-GB", 1.10f, 1.06f),
        Profile(3, "Voice 3 · calm", "en-US", 0.95f, 0.90f)
    )

    fun of(id: Int): Profile = all.getOrElse(id) { all[0] }

    fun next(id: Int): Int = (id + 1) % all.size
}
