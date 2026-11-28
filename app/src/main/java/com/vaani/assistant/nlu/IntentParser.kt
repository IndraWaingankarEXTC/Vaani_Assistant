package com.vaani.assistant.nlu

import com.vaani.assistant.compress.Sizes
import com.vaani.assistant.core.Text
import com.vaani.assistant.core.Vlog

/**
 * Rule-based, zero-dependency intent parser.
 *
 * Deliberately not an LLM: on a 2 GB phone the rule grammar answers in microseconds, costs no
 * memory, and never invents an action. It covers the everyday command surface (apps, media,
 * calls, messages, alarms, timers, settings, screen actions) plus Hinglish verb forms. Anything
 * it cannot parse becomes [Command.Unknown] and is offered to the optional tiny LLM planner.
 */
class IntentParser {

    private val verbs = listOf(
        "open", "launch", "start", "run", "play", "pause", "resume", "next", "previous", "prev",
        "skip", "set", "create", "make", "call", "dial", "phone", "send", "message", "whatsapp",
        "text", "search", "google", "find", "look", "turn", "switch", "enable", "disable", "tap",
        "click", "press", "type", "enter", "write", "scroll", "swipe", "go", "read", "navigate",
        "directions", "repeat", "volume", "flashlight", "torch", "timer", "alarm", "stop", "mute",
        "louder", "quieter", "add", "put", "wake", "count"
    )

    private val splitter = Regex(
        "\\s*(?:,\\s*and then|\\s+and then|\\s+then|\\s*;)\\s*|\\s+and\\s+(?=(?:${verbs.joinToString("|")})\\b)"
    )

    /** "spotify kholo" normalises to "spotify open"; put the verb back in front. */
    private val trailingVerb = Regex(
        "^(?<obj>.+?)\\s+(?<verb>open|play|send|set|search|call|turn on|turn off|volume up|volume down|wake me up|what)$"
    )

    fun parse(raw: String): Command {
        Vlog.i("NLU", "heard [${LanguageDetector.describe(raw)}] $raw")
        val pieces = split(raw).map { parseOne(it) }
        val useful = pieces.filter { it !is Command.Unknown }
        // "open amazon and search for headphones" must not become open + web search.
        if (useful.size == 2) {
            val first = useful[0]
            val second = useful[1]
            if (first is Command.OpenApp && first.query == null) {
                val q = when (second) {
                    is Command.WebSearch -> second.query
                    is Command.SearchInApp -> second.query
                    is Command.OpenApp -> second.query
                    else -> null
                }
                if (q != null) return Command.OpenApp(first.app, q)
            }
        }
        return when {
            useful.isEmpty() -> Command.Unknown(raw)
            useful.size == 1 -> useful[0]
            else -> Command.Compound(useful)
        }
    }

    private val verbWord = Regex("\\b(?:${verbs.joinToString("|")})\\b")

    private fun hasVerb(piece: String): Boolean =
        verbWord.containsMatchIn(CodeMixLexicon.rewrite(piece))

    private fun split(raw: String): List<String> {
        // 1. A bare comma between two commands is normal in code-mixed speech:
        //    "spotify kholo, lofi bajao". Normalisation strips punctuation, so this must run on
        //    the raw string, and only when both halves actually carry a verb.
        val byComma = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(raw) }
        val pieces = if (byComma.size > 1 && byComma.count { hasVerb(it) } > 1) byComma else listOf(raw)

        // 2. Then the spoken connectors, on the canonical form: "aur"/"phir" only become
        //    "and"/"then" after the lexicon has run.
        return pieces.flatMap { piece ->
            CodeMixLexicon.rewrite(piece).split(splitter)
                .map { it.trim() }.filter { it.isNotEmpty() }
                .ifEmpty { listOf(piece) }
        }
    }

    private fun canon(n: String): String = n
        .replace("wi fi", "wifi")
        .replace("do not disturb", "dnd")
        .replace("airplane mode", "airplane")
        .replace("flight mode", "airplane")
        .replace("mobile data", "data")
        .replace("cellular data", "data")
        .replace(Regex("\\bko\\b"), " ")

    private fun parseOne(input: String): Command {
        // One rewriter for every language: native script is transliterated, then each token is
        // mapped to a canonical English word. Code-mixing therefore costs nothing downstream.
        // The lexicon gets the raw utterance first: normalising before it would strip the
        // vowel signs that carry the meaning in every Indic script.
        var n = canon(Text.stripFillers(CodeMixLexicon.rewrite(input)))
        n = n.replace(Regex("\\s+"), " ").trim()
        n = n.trim()
            .removePrefix("and ").removePrefix("then ")
            .removeSuffix(" and").removeSuffix(" then").trim()
        trailingVerb.find(n)?.let { m ->
            n = m.groups["verb"]!!.value + " " + m.groups["obj"]!!.value
        }
        if (n.isEmpty()) return Command.Unknown(input)

        // ---- media transport ----
        if (Regex("^(pause|stop|hold)( the)?( music| song| track| playback| audio| video| it)?$").matches(n))
            return Command.Media(if (n.startsWith("stop")) MediaAction.STOP else MediaAction.PAUSE)
        if (Regex("^(play|resume|continue|unpause)( the)?( music| song| track| playback| audio)?$").matches(n))
            return Command.Media(MediaAction.PLAY)
        if (Regex("^(next|skip)( the| this)?( song| track| one)?$|^skip ahead$").matches(n))
            return Command.Media(MediaAction.NEXT)
        if (Regex("^(previous|prev|last)( the| this)?( song| track| one)?$|^go back a (song|track)$").matches(n))
            return Command.Media(MediaAction.PREV)
        if (Regex("^(toggle|switch)( the)?( music| song| playback)$").matches(n))
            return Command.Media(MediaAction.TOGGLE)

        // ---- volume ----
        if (Regex("^(volume up|turn (the )?volume up|turn up the volume|increase (the )?volume|louder|up the volume|volume increase)$").matches(n))
            return Command.Volume(true)
        if (Regex("^(volume down|turn (the )?volume down|turn down the volume|decrease (the )?volume|lower (the )?volume|quieter|down the volume|volume decrease)$").matches(n))
            return Command.Volume(false)

        // ---- on/off toggles ----
        Regex("^(turn|switch) (on|off) (the )?(?<what>flashlight|torch|wifi|bluetooth|airplane|dnd|hotspot|data)$")
            .find(n)?.let { m ->
                return Command.Toggle(m.groups["what"]!!.value, m.groupValues[2] == "on")
            }
        Regex("^(enable|disable|start|stop) (the )?(?<what>wifi|bluetooth|flashlight|torch|dnd|airplane|hotspot|data)$")
            .find(n)?.let { m ->
                val on = m.groupValues[1] == "enable" || m.groupValues[1] == "start"
                return Command.Toggle(m.groups["what"]!!.value, on)
            }
        Regex("^(?<what>wifi|bluetooth|flashlight|torch|dnd|airplane|hotspot|data) (on|off)$")
            .find(n)?.let { m -> return Command.Toggle(m.groups["what"]!!.value, m.groupValues[2] == "on") }

        // ---- alarm ----
        Regex("^(set|create|make|put|add) (an? )?alarm (for |at )?(?<t>.+)$").find(n)?.let { m ->
            return alarmOrUnknown(m.groups["t"]!!.value, input)
        }
        Regex("^alarm (for |at )?(?<t>.+)$").find(n)?.let { m ->
            return alarmOrUnknown(m.groups["t"]!!.value, input)
        }
        Regex("^wake me up (at |in )?(?<t>.+)$").find(n)?.let { m ->
            return alarmOrUnknown(m.groups["t"]!!.value, input)
        }

        // ---- timer ----
        Regex("^(set|start|create) (an? )?timer (for )?(?<t>.+)$").find(n)?.let { m ->
            TimeParser.parseDurationSeconds(m.groups["t"]!!.value)?.let { return Command.SetTimer(it) }
        }
        Regex("^timer (for )?(?<t>.+)$").find(n)?.let { m ->
            TimeParser.parseDurationSeconds(m.groups["t"]!!.value)?.let { return Command.SetTimer(it) }
        }
        Regex("^(count|countdown) (down )?(for )?(?<t>.+)$").find(n)?.let { m ->
            TimeParser.parseDurationSeconds(m.groups["t"]!!.value)?.let { return Command.SetTimer(it) }
        }

        // ---- messaging (must run before "call" and "open") ----
        // Indian word order puts the recipient first: "amma ko message bhejo saying aa rahi hoon".
        Regex("^(?<who>[\\p{L}\\s.'-]+?) (?:ko )?(?:whatsapp|message|msg|sms|text) send(?: saying| that says| bolo| bol do| ki|:)? (?<body>.+)$")
            .find(n)?.let { m ->
                val who = m.groups["who"]!!.value.trim()
                val body = m.groups["body"]!!.value.trim()
                val viaWhatsApp = n.contains("whatsapp")
                return if (viaWhatsApp) Command.SendWhatsApp(who, body) else Command.SendSms(who, body)
            }
        Regex("^(?<who>[\\p{L}\\s.'-]+?) (?:ko )?(?:whatsapp|message|msg|sms|text) send$")
            .find(n)?.let { m ->
                val who = m.groups["who"]!!.value.trim()
                return if (n.contains("whatsapp")) Command.SendWhatsApp(who, "") else Command.SendSms(who, "")
            }

        Regex("^(send )?(an? )?whatsapp (message )?(to )?(?<who>[\\p{L}\\s.'-]+?)( saying | that says |: )(?<body>.+)$")
            .find(n)?.let { m -> return Command.SendWhatsApp(m.groups["who"]!!.value.trim(), m.groups["body"]!!.value.trim()) }
        Regex("^(send )?(an? )?whatsapp (message )?(to )?(?<who>[\\p{L}\\s.'-]+)$")
            .find(n)?.let { m -> return Command.SendWhatsApp(m.groups["who"]!!.value.trim(), "") }
        Regex("^(send )?(a )?message (to )?(?<who>[\\p{L}\\s.'-]+?) on whatsapp( saying | that says |: )(?<body>.+)$")
            .find(n)?.let { m -> return Command.SendWhatsApp(m.groups["who"]!!.value.trim(), m.groups["body"]!!.value.trim()) }
        Regex("^(send )?(an? )?(sms|text|message|msg) (to )?(?<who>[\\p{L}\\s.'-]+?)( saying | that says | with message |: )(?<body>.+)$")
            .find(n)?.let { m -> return Command.SendSms(m.groups["who"]!!.value.trim(), m.groups["body"]!!.value.trim()) }
        Regex("^(send )?(an? )?(sms|text|message|msg) (to )?(?<who>[\\p{L}\\s.'-]+)$")
            .find(n)?.let { m -> return Command.SendSms(m.groups["who"]!!.value.trim(), "") }

        // ---- calls ----
        Regex("^(call|dial|phone|ring) (up )?(?<who>[\\p{L}\\s.'-]+?)( back)?$").find(n)?.let { m ->
            val who = m.groups["who"]!!.value.trim()
            if (who.isNotEmpty() && who != "history" && who != "log") return Command.Call(who)
        }

        // ---- dictation (before the generic "type ..." rule, which would eat "type mode") ----
        if (Regex("^(start |begin |enter |open )?(dictation|dictating|dictate|voice typing|voice type|typing mode|type mode)( mode)?$").matches(n))
            return Command.Dictate
        if (Regex("^(start|begin|shuru) (dictation|dictating|typing|voice typing|type mode)( mode)?$").matches(n))
            return Command.Dictate
        if (Regex("^(stop|end|exit|finish|band|close) (dictation|dictating|typing|voice typing|type mode)( mode)?$").matches(n))
            return Command.StopDictation
        if (Regex("^(bas|chalo|enough) (likhna|typing|dictation)$").matches(n))
            return Command.StopDictation

        // ---- emoji, spotify, cinema ----
        Regex("^(?:send|type|insert|add) (?:a |an )?(?<name>[a-z ]+?) emoji$").find(n)?.let { m ->
            return Command.SendEmoji(m.groups["name"]!!.value.trim())
        }
        Regex("^emoji (?<name>.+)$").find(n)?.let { m ->
            return Command.SendEmoji(m.groups["name"]!!.value.trim())
        }
        Regex("^(?:play|bajao) (?<q>.+?) (?:on|in) spotify$").find(n)?.let { m ->
            return Command.SpotifyPlay(m.groups["q"]!!.value.trim())
        }
        Regex("^spotify (?:play |search |bajao )?(?<q>.+)$").find(n)?.let { m ->
            return Command.SpotifyPlay(m.groups["q"]!!.value.trim())
        }
        if (Regex("^(?:book|buy) (?:movie |cinema |film )?tickets?$|^book ?my ?show$|^book tickets? on book ?my ?show$").matches(n))
            return Command.BookMovies
        if (Regex("^(?:new|latest|now showing|running) movies?$|^what movies.*$|^movies? (?:out|running)$").matches(n))
            return Command.NewMovies

        // ---- memory, notes, notifications, reading, vault ----
        Regex("^remember (this|that|these|it)( as| is| are)? ?(?<label>.+)?$").find(n)?.let { m ->
            return Command.Remember(m.groups["label"]?.value?.trim().orEmpty())
        }
        Regex("^where (is|are|did i (put|keep)) (?<thing>.+)$").find(n)?.let { m ->
            return Command.WhereIs(m.groups["thing"]!!.value.trim())
        }
        if (Regex("^(take|make|record|add) (a )?note$|^new note$").matches(n)) return Command.TakeNote
        Regex("^(find|search) (my )?notes? (about|for|on) (?<q>.+)$").find(n)?.let { m ->
            return Command.FindNote(m.groups["q"]!!.value.trim())
        }
        if (Regex("^(what|what s) new$|^(read|show) (my )?notifications$|^any notifications$").matches(n))
            return Command.Notifications
        if (Regex("^(read|read out|speak) (this|it|the screen|this screen)$|^read aloud$").matches(n))
            return Command.ReadAloud
        if (Regex("^(next paragraph|next part|continue reading)$").matches(n)) return Command.ReadAloudControl("next")
        if (Regex("^(previous paragraph|previous part|back a paragraph)$").matches(n)) return Command.ReadAloudControl("back")
        if (Regex("^(read )?(faster|speed up)$").matches(n)) return Command.ReadAloudControl("faster")
        if (Regex("^(read )?(slower|slow down)$").matches(n)) return Command.ReadAloudControl("slower")
        if (Regex("^stop reading$|^stop the reading$").matches(n)) return Command.ReadAloudControl("stop")
        Regex("^(fill|type|enter) (in )?(my )?(?<key>address|email|name|phone|mobile|pin ?code|upi|city)( ?(id|number|address))?$")
            .find(n)?.let { m ->
                return Command.FillField(m.groups["key"]!!.value.trim())
            }
        Regex("^save (my )?(?<key>address|email|name|phone|mobile|pin ?code|upi|city) as (?<value>.+)$")
            .find(n)?.let { m ->
                return Command.SaveDetail(m.groups["key"]!!.value.trim(), m.groups["value"]!!.value.trim())
            }
        if (Regex("^(convert|converter|convert a file|make a pdf)$").matches(n)) return Command.ConvertFile
        Regex("^(compress|shrink|reduce) (this|it|the file|this file)? ?(to |under |below )?(?<size>.+)$")
            .find(n)?.let { m ->
                val bytes = Sizes.parse(m.groups["size"]!!.value)
                if (bytes != null) return Command.CompressFile(bytes)
            }
        Regex("^(convert|change) (this|it|the file)? ?(to|into) (?<fmt>pdf|jpe?g|png)$").find(n)?.let { m ->
            return Command.ConvertTo(m.groups["fmt"]!!.value)
        }
        Regex("^(compress|shrink|reduce) (this|it|the file|this file)? ?(to |under |below )?(?<size>.+)$")
            .find(n)?.let { m ->
                val bytes = Sizes.parse(m.groups["size"]!!.value)
                if (bytes != null) return Command.CompressFile(bytes)
            }
        Regex("^(convert|change) (this|it|the file)? ?(to|into) (?<fmt>pdf|jpe?g|png)$").find(n)?.let { m ->
            return Command.ConvertTo(m.groups["fmt"]!!.value)
        }

        // ---- clipboard and inbox ----
        if (Regex("^(save|keep|store) (this|it|the link|this link)( in (my )?inbox)?$").matches(n))
            return Command.SaveClip
        if (Regex("^(what|what s) (did|do) i copy|^read (the )?clipboard$|^(what|what s) on (the )?clipboard$").matches(n))
            return Command.ReadClipboard
        if (Regex("^(show|open|read) (my )?inbox$|^(what|what s) in (my )?inbox$|^inbox$").matches(n))
            return Command.ListInbox

        // ---- lens (before the open-app rule, which would hunt for an app called "lens") ----
        if (Regex("^(open |launch |start |bring up )?(the )?(lens|scanner|camera mode|lens mode|scan mode)( mode)?$").matches(n))
            return Command.OpenLens
        if (Regex("^(scan|identify|look at|what is|what s) (this|that|it)( device)?$").matches(n))
            return Command.OpenLens

        // ---- navigation ----
        Regex("^(navigate|directions|route|drive|take me|get me) to (?<place>.+)$").find(n)?.let { m ->
            return Command.Navigate(m.groups["place"]!!.value.trim())
        }

        // ---- open an app, optionally with a search inside it ----
        Regex("^(open|launch|start|run|go to|switch to|bring up) (?<rest>.+)$").find(n)?.let { m ->
            val rest = m.groups["rest"]!!.value.trim()
            val sub = Regex("^(?<app>.+?) (?:and )?(?:search|look|find|play|show me)(?: for)? (?<q>.+)$").find(rest)
            if (sub != null) {
                return Command.OpenApp(sub.groups["app"]!!.value.trim(), sub.groups["q"]!!.value.trim())
            }
            return Command.OpenApp(rest)
        }

        // ---- search ----
        Regex("^(search|find|look for|look up) (?:on|in) (?<app>[\\p{L}\\s]+?) (?:for )?(?<q>.+)$").find(n)?.let { m ->
            return Command.SearchInApp(m.groups["app"]!!.value.trim(), m.groups["q"]!!.value.trim())
        }
        Regex("^(search|google|look up|look for|find) (?:for )?(?<q>.+?) (?:on|in|using) (?<app>[\\p{L}\\s]+)$")
            .find(n)?.let { m ->
                return Command.SearchInApp(m.groups["app"]!!.value.trim(), m.groups["q"]!!.value.trim())
            }
        Regex("^(search|google|look up|look for|find) (?:for )?(?<q>.+)$").find(n)?.let { m ->
            return Command.WebSearch(m.groups["q"]!!.value.trim())
        }

        // ---- play something (after the bare transport forms above) ----
        Regex("^(play|put on|stream) (?<q>.+?) (?:on|in|using) (?<app>[\\p{L}\\s]+)$").find(n)?.let { m ->
            return Command.OpenApp(m.groups["app"]!!.value.trim(), m.groups["q"]!!.value.trim())
        }
        Regex("^(play|put on) (?<q>.+)$").find(n)?.let { m ->
            return Command.OpenApp("music", m.groups["q"]!!.value.trim())
        }

        // ---- screen actions ----
        Regex("^(tap|click|press|touch|hit) (?:on )?(?<t>.+)$").find(n)?.let { m ->
            return Command.Tap(m.groups["t"]!!.value.trim())
        }
        Regex("(?i)^(type|enter|write|input) (?<t>.+)$").find(input.trim())?.let { m ->
            return Command.TypeText(m.groups["t"]!!.value.trim())
        }
        Regex("^(scroll|swipe) (?<dir>up|down|left|right)( .*)?$").find(n)?.let { m ->
            return Command.Scroll(m.groups["dir"]!!.value == "down" || m.groups["dir"]!!.value == "up")
        }

        // ---- navigation between screens ----
        if (Regex("^(go )?back$|^previous screen$|^(close|dismiss) (it|this|the app)$").matches(n))
            return Command.Back
        if (Regex("^(go )?home$|^home screen$|^go to (the )?home screen$").matches(n))
            return Command.Home
        if (Regex("^(recents|recent apps|show recent apps|open recent apps)$").matches(n))
            return Command.Recents

        // ---- reading the screen ----
        if (Regex("^read( the)? screen$|^read (it|this)( out)?$|^(what|what s|whats|what is|what are|tell me what s) .*(screen|on it|here)$").matches(n))
            return Command.ReadScreen

        if (Regex("^(repeat|again|do it again|say again|say that again)$").matches(n))
            return Command.Repeat

        Vlog.i("NLU", "no rule matched: '$n'")
        return Command.Unknown(input)
    }

    private fun alarmOrUnknown(spec: String, input: String): Command {
        // "an alarm in 20 minutes" is a duration, not a clock time of 20:00.
        if (Regex("\\b(hours?|hrs?|minutes?|mins?|seconds?|secs?)\\b").containsMatchIn(spec)) {
            TimeParser.parseDurationSeconds(spec)?.let { sec ->
                val c = TimeParser.clockFromNow(sec)
                return Command.SetAlarm(c.hour, c.minute)
            }
        }
        TimeParser.parseClock(spec)?.let { return Command.SetAlarm(it.hour, it.minute) }
        TimeParser.parseDurationSeconds(spec)?.let { sec ->
            val c = TimeParser.clockFromNow(sec)
            return Command.SetAlarm(c.hour, c.minute)
        }
        return Command.Unknown(input)
    }
}
