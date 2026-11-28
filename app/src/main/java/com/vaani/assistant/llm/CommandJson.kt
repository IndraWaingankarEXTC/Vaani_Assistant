package com.vaani.assistant.llm

import com.vaani.assistant.nlu.Command
import com.vaani.assistant.nlu.MediaAction
import org.json.JSONObject

/**
 * One compact JSON shape shared by the optional LLM planner and by taught recipes, so a model
 * never gets to invent an action outside [Command].
 */
object CommandJson {

    fun parse(json: String): Command? {
        val start = json.indexOf('{')
        val end = json.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = runCatching { JSONObject(json.substring(start, end + 1)) }.getOrNull() ?: return null
        return when (o.optString("cmd")) {
            "open_app" -> Command.OpenApp(o.optString("app"), o.optString("query").ifBlank { null })
            "media" -> Command.Media(
                when (o.optString("action")) {
                    "play" -> MediaAction.PLAY
                    "pause" -> MediaAction.PAUSE
                    "next" -> MediaAction.NEXT
                    "prev" -> MediaAction.PREV
                    "stop" -> MediaAction.STOP
                    else -> MediaAction.TOGGLE
                }
            )
            "call" -> Command.Call(o.optString("who"))
            "sms" -> Command.SendSms(o.optString("who"), o.optString("text"))
            "whatsapp" -> Command.SendWhatsApp(o.optString("who"), o.optString("text"))
            "alarm" -> Command.SetAlarm(o.optInt("hour", 7), o.optInt("minute", 0))
            "timer" -> Command.SetTimer(o.optInt("seconds", 300))
            "navigate" -> Command.Navigate(o.optString("place"))
            "search" -> Command.WebSearch(o.optString("query"))
            "search_in" -> Command.SearchInApp(o.optString("app"), o.optString("query"))
            "toggle" -> Command.Toggle(o.optString("setting"), o.optBoolean("on", true))
            "tap" -> Command.Tap(o.optString("text"))
            "type" -> Command.TypeText(o.optString("text"), o.optBoolean("submit", false))
            "scroll" -> Command.Scroll(o.optBoolean("forward", true))
            "back" -> Command.Back
            "home" -> Command.Home
            "read_screen" -> Command.ReadScreen
            else -> null
        }
    }

    fun schema(): String = """
        Reply with one JSON object, nothing else. Allowed forms:
        {"cmd":"open_app","app":"<app name>","query":"<optional search>"}
        {"cmd":"media","action":"play|pause|next|prev|stop"}
        {"cmd":"call","who":"<contact>"}
        {"cmd":"sms","who":"<contact>","text":"<message>"}
        {"cmd":"whatsapp","who":"<contact>","text":"<message>"}
        {"cmd":"alarm","hour":0-23,"minute":0-59}
        {"cmd":"timer","seconds":<int>}
        {"cmd":"navigate","place":"<place>"}
        {"cmd":"search","query":"<text>"}
        {"cmd":"search_in","app":"<app>","query":"<text>"}
        {"cmd":"toggle","setting":"wifi|bluetooth|flashlight|dnd|airplane|data","on":true|false}
        {"cmd":"tap","text":"<visible label>"}
        {"cmd":"type","text":"<text>","submit":true|false}
        {"cmd":"scroll","forward":true|false}
        {"cmd":"back"} {"cmd":"home"} {"cmd":"read_screen"}
        If none fits, reply {"cmd":"unknown"}.
    """.trimIndent()
}
