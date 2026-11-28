package com.vaani.assistant.exec

import android.content.Intent
import com.vaani.assistant.nlu.MediaAction

/** One unit of work in a plan. Plans are just ordered lists of these. */
sealed class Step {
    data class Launch(val pkg: String, val uri: String? = null) : Step()
    data class OpenUri(val uri: String, val pkg: String? = null) : Step()
    data class WaitFor(val label: String, val timeoutMs: Long = 6000) : Step()
    data class Tap(val label: String, val timeoutMs: Long = 6000) : Step()
    data class FocusSearch(val timeoutMs: Long = 5000) : Step()
    data class Type(val text: String, val submit: Boolean = false) : Step()
    data class Scroll(val forward: Boolean = true) : Step()
    data class Pause(val ms: Long) : Step()
    data class Raw(val intent: Intent) : Step()
    data class Media(val action: MediaAction) : Step()
    data class Volume(val up: Boolean) : Step()
    data class Flashlight(val on: Boolean) : Step()
    data class SendSms(val number: String, val text: String) : Step()
    data class Dnd(val on: Boolean) : Step()
    data class Say(val text: String) : Step()
    data class Confirm(val question: String) : Step()
    object Back : Step()
    object Home : Step()
    object Recents : Step()
    object ReadScreen : Step()
}

/** A plan plus the line Vaani speaks before it starts. */
data class Plan(val steps: List<Step>, val say: String? = null)
