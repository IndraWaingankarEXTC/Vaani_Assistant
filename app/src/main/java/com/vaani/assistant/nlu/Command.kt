package com.vaani.assistant.nlu

/** Transport actions we can drive through a MediaSession without touching the app UI. */
enum class MediaAction { PLAY, PAUSE, TOGGLE, NEXT, PREV, STOP }

/**
 * The closed set of things Vaani can be asked to do. Everything the parser understands lands
 * in one of these; anything it does not understand becomes [Unknown] and is either handed to the
 * optional on-device LLM or reported back to the user.
 */
sealed class Command {
    data class OpenApp(val app: String, val query: String? = null) : Command()
    data class Call(val who: String) : Command()
    data class SendSms(val who: String, val text: String) : Command()
    data class SendWhatsApp(val who: String, val text: String) : Command()
    data class SetAlarm(val hour: Int, val minute: Int, val label: String? = null) : Command()
    data class SetTimer(val seconds: Int, val label: String? = null) : Command()
    data class Media(val action: MediaAction) : Command()
    data class Volume(val up: Boolean) : Command()
    data class Navigate(val place: String) : Command()
    data class WebSearch(val query: String) : Command()
    data class SearchInApp(val app: String, val query: String) : Command()
    data class Toggle(val setting: String, val on: Boolean) : Command()
    data class Tap(val text: String) : Command()
    data class TypeText(val text: String, val submit: Boolean = false) : Command()
    data class Scroll(val forward: Boolean) : Command()
    object Back : Command()
    object Home : Command()
    object Recents : Command()
    object ReadScreen : Command()
    object Repeat : Command()
    /** Enter sentence dictation: every utterance is appended to the focused text field. */
    object Dictate : Command()
    object StopDictation : Command()
    /** Full-screen camera lens: four corners around a device, then name it. */
    object OpenLens : Command()
    /** Keep whatever is on the clipboard in the local inbox. */
    object SaveClip : Command()
    /** Read the clipboard back. */
    object ReadClipboard : Command()
    /** How many things are in the inbox, and what the newest is. */
    object ListInbox : Command()
    /** Keep what the camera (or the screen) is looking at, under a name you choose. */
    data class Remember(val label: String) : Command()
    /** "where are my keys" */
    data class WhereIs(val thing: String) : Command()
    /** Dictate a note and keep it. */
    object TakeNote : Command()
    /** "find my note about the plumber" */
    data class FindNote(val query: String) : Command()
    /** Summarise what arrived. */
    object Notifications : Command()
    /** Read the screen out loud. */
    object ReadAloud : Command()
    /** next / back / faster / slower / stop, while reading. */
    data class ReadAloudControl(val action: String) : Command()
    /** Type a saved detail into the focused field. */
    data class FillField(val key: String) : Command()
    /** Remember a detail: "save my address as 12 MG Road". */
    data class SaveDetail(val key: String, val value: String) : Command()
    /** Open the converter. */
    object ConvertFile : Command()
    /** Open the converter already set to squeeze a file under a byte budget. */
    data class CompressFile(val targetBytes: Int) : Command()
    /** Open the converter for a specific target format. */
    data class ConvertTo(val format: String) : Command()
    data class Compound(val parts: List<Command>) : Command()
    data class Unknown(val raw: String) : Command()
}
