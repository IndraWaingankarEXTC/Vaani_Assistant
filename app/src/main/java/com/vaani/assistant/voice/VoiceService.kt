package com.vaani.assistant.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vaani.assistant.MainActivity
import com.vaani.assistant.R
import com.vaani.assistant.core.Inbox
import com.vaani.assistant.convert.ConverterActivity
import com.vaani.assistant.lens.LensActivity
import com.vaani.assistant.memory.ObjectMemory
import com.vaani.assistant.nlu.MediaAction
import com.vaani.assistant.notes.VoiceNotes
import com.vaani.assistant.notify.NotificationDigest
import com.vaani.assistant.exec.VaaniNotificationListener
import com.vaani.assistant.vault.Vault
import com.vaani.assistant.vault.VaultKeys
import com.vaani.assistant.core.Prefs
import com.vaani.assistant.core.Text
import com.vaani.assistant.core.Vlog
import com.vaani.assistant.exec.Executor
import com.vaani.assistant.exec.MediaControl
import com.vaani.assistant.exec.MemoryGovernor
import com.vaani.assistant.exec.Plan
import com.vaani.assistant.exec.Planner
import com.vaani.assistant.exec.RecipeStore
import com.vaani.assistant.exec.ScreenSummary
import com.vaani.assistant.llm.LlmPlanner
import com.vaani.assistant.nlu.AppRegistry
import com.vaani.assistant.nlu.Command
import com.vaani.assistant.nlu.ContactResolver
import com.vaani.assistant.nlu.IntentParser
import com.vaani.assistant.nlu.LanguageDetector
import com.vaani.assistant.ui.AccessibilityAgent
import com.vaani.assistant.ui.SummaryPopup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The ear and the orchestrator.
 *
 * Two ways in: hands-free (audio gate -> wake phrase -> command, then a follow-up window) and
 * push-to-talk (bubble or notification, no wake word). Either way the pipeline is the same:
 * listen -> parse (any mix of languages) -> plan -> execute -> reply in the language you used.
 */
class VoiceService : Service() {

    companion object {
        const val ACTION_LISTEN = "com.vaani.action.LISTEN"
        const val ACTION_TEXT = "com.vaani.action.TEXT"
        const val ACTION_STOP = "com.vaani.action.STOP"
        const val ACTION_BUBBLE = "com.vaani.action.BUBBLE"
        const val ACTION_HANDS_FREE = "com.vaani.action.HANDS_FREE"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_ON = "extra_on"
        private const val CHANNEL_ID = "vaani_voice"
        private const val NOTIF_ID = 1001

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, VoiceService::class.java))
        }

        fun listen(ctx: Context) {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, VoiceService::class.java).setAction(ACTION_LISTEN)
            )
        }

        fun runText(ctx: Context, text: String) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, VoiceService::class.java).setAction(ACTION_TEXT).putExtra(EXTRA_TEXT, text)
            )
        }

        fun setBubble(ctx: Context, on: Boolean) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, VoiceService::class.java).setAction(ACTION_BUBBLE).putExtra(EXTRA_ON, on)
            )
        }

        fun setHandsFree(ctx: Context, on: Boolean) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, VoiceService::class.java).setAction(ACTION_HANDS_FREE).putExtra(EXTRA_ON, on)
            )
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, VoiceService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var asr: AsrEngine
    private lateinit var tts: Tts
    private lateinit var executor: Executor
    private lateinit var parser: IntentParser
    private lateinit var planner: Planner
    private lateinit var recipes: RecipeStore
    private lateinit var apps: AppRegistry
    private lateinit var llm: LlmPlanner
    private lateinit var wake: WakeWordEngine
    private lateinit var governor: MemoryGovernor

    private var bubble: OverlayBubble? = null
    private var popup: SummaryPopup? = null
    private var busy = false
    private var readChunks: List<String> = emptyList()
    private var readIndex = 0
    private var lastCommand: Command? = null
    private var lastMix: LanguageDetector.LanguageMix? = null
    private var dictating = false
    private var lastDictationAt = 0L
    private var memoryTicker: Job? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        createChannel()
        goForeground()

        apps = AppRegistry(this)
        parser = IntentParser()
        planner = Planner(this, apps, ContactResolver(this))
        recipes = RecipeStore(this)
        llm = LlmPlanner(this)
        governor = MemoryGovernor(this)
        tts = Tts(this).also { it.init() }

        executor = Executor(this, ::say, MediaControl(this))
        executor.confirmHandler = ::askConfirm
        executor.commitHandler = ::askCommit
        executor.mixProvider = { lastMix }

        asr = AsrEngine(this, object : AsrEngine.Callbacks {
            override fun onPartial(text: String) = Vlog.i("ASR", "… $text")
            override fun onFinal(text: String) = handle(text)
            override fun onError(message: String) {
                Vlog.i("ASR", message)
                if (message != "I didn't catch that") say(message)
            }
        })

        wake = WakeWordEngine(
            ctx = this,
            onCommand = { text ->
                when {
                    // Mid-dictation a bare wake word should not start talking over the user.
                    text.isBlank() -> if (dictating) wake.restart() else promptForCommand()
                    dictating -> handleDictation(text)
                    else -> handle(text)
                }
            },
            onBargeIn = { tts.stop() },
            isFollowUp = { followUpOpen || dictating },
            isBusy = { busy || tts.isSpeaking }
        )

        if (Prefs.bubbleEnabled) showBubble(true)
        if (Prefs.wakeWordEnabled) wake.start()
        if (Prefs.llmEnabled) scope.launch(Dispatchers.IO) { llm.warmUp() }
        startMemoryTicker()
        warnIfInterruptedCommit()
        Vlog.i("Svc", "ready · hands-free=${Prefs.wakeWordEnabled} · phrase='${Prefs.wakePhrase}'")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LISTEN -> beginListening()
            ACTION_TEXT -> intent.getStringExtra(EXTRA_TEXT)?.let { handle(it) }
            ACTION_BUBBLE -> {
                val on = intent.getBooleanExtra(EXTRA_ON, false)
                Prefs.bubbleEnabled = on
                showBubble(on)
            }
            ACTION_HANDS_FREE -> {
                val on = intent.getBooleanExtra(EXTRA_ON, false)
                Prefs.wakeWordEnabled = on
                if (on) wake.start() else wake.stop()
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> Unit
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (governor.onTrim(level)) llm.release()
    }

    override fun onDestroy() {
        memoryTicker?.cancel()
        scope.cancel()
        asr.cancel()
        wake.stop()
        dictating = false
        tts.shutdown()
        popup?.dismiss()
        llm.release()
        bubble?.hide()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- listening

    private var followUpOpen = false

    private fun beginListening() {
        if (busy) {
            say(Reply.say("busy", lastMix))
            return
        }
        if (!AccessibilityAgent.ready()) {
            Vlog.i("Svc", "screen control is off; app launching still works")
        }
        wake.listenNow()
    }

    private fun promptForCommand() {
        say(Reply.say("listening", lastMix))
        followUpOpen = true
        wake.armFollowUp(Prefs.followUpMs)
        wake.restart()
        scope.launch {
            delay(Prefs.followUpMs)
            followUpOpen = false
        }
    }

    private suspend fun listenOnce(): String? = suspendCancellableCoroutine { cont ->
        var engine: AsrEngine? = null
        engine = AsrEngine(this, object : AsrEngine.Callbacks {
            override fun onPartial(text: String) = Unit
            override fun onFinal(text: String) {
                engine?.cancel()
                if (cont.isActive) cont.resume(text)
            }
            override fun onError(message: String) {
                if (cont.isActive) cont.resume(null)
            }
        })
        engine.languageTag = Prefs.asrLanguage
        cont.invokeOnCancellation { engine?.cancel() }
        engine.start()
    }

    private suspend fun askConfirm(question: String): Boolean {
        say(question)
        delay(500)
        val answer = listenOnce() ?: return false
        val n = Text.norm(answer)
        return n.contains("confirm") || n.contains("yes") || n.contains("haan") ||
            n.contains("kar do") || n == "ok" || n == "okay" || n.contains("go ahead") ||
            n.contains("theek hai") || n.contains("sari")
    }

    /**
     * The order gate. Shows the summary card over the shopping app, speaks the same facts, and
     * takes whichever answer arrives first — a tap on the card or a spoken yes. Silence, a
     * timeout, or anything ambiguous is a no.
     */
    private suspend fun askCommit(summary: ScreenSummary): Boolean {
        if (!Prefs.confirmOrders) {
            say(Reply.say("blocked_payment", lastMix))
            return false
        }
        val card = popup ?: SummaryPopup(this).also { popup = it }

        return suspendCancellableCoroutine { cont ->
            var settled = false
            fun finish(ok: Boolean) {
                if (settled) return
                settled = true
                card.dismiss()
                Vlog.i("Gate", if (ok) "approved by user" else "declined or timed out")
                if (cont.isActive) cont.resume(ok)
            }

            val shown = card.show(summary) { ok -> finish(ok) }
            say(Reply.say("commit_check", lastMix, summary.facts()))
            if (!shown) Vlog.i("Gate", "no overlay; voice-only confirmation")

            scope.launch {
                // Let the spoken summary finish before opening the mic, or we hear ourselves.
                var waited = 0
                while (tts.isSpeaking && waited < 8_000) {
                    delay(200)
                    waited += 200
                }
                delay(400)
                val answer = listenOnce() ?: return@launch
                val n = Text.norm(answer)
                when {
                    isYes(n) -> finish(true)
                    isNo(n) -> finish(false)
                    else -> Vlog.i("Gate", "ambiguous answer: $answer")
                }
            }

            cont.invokeOnCancellation { card.dismiss() }
        }
    }

    private fun isYes(n: String): Boolean =
        listOf("yes", "yeah", "yep", "ok", "okay", "looks good", "go ahead", "confirm", "proceed",
            "haan", "han", "theek hai", "thik hai", "sahi hai", "kar do", "aage badho",
            "sari", "sare", "aachu", "thik ache", "sari hai")
            .any { n == it || n.contains(" $it ") || n.startsWith("$it ") || n.endsWith(" $it") }

    private fun isNo(n: String): Boolean =
        listOf("no", "nope", "cancel", "stop", "nahi", "nahin", "mat karo", "ruko", "band",
            "venda", "vaddu", "illa", "na")
            .any { n == it || n.contains(" $it ") || n.startsWith("$it ") || n.endsWith(" $it") }

    /**
     * The phone died between journaling a commit and clearing it. Say so rather than assume the
     * tap never happened — a duplicate order is worse than a wasted check.
     */
    private fun warnIfInterruptedCommit() {
        val marker = Prefs.pendingCommit ?: return
        Prefs.pendingCommit = null
        val parts = marker.split('|')
        val label = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "an order"
        val stamped = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        val age = System.currentTimeMillis() - stamped
        Vlog.i("Gate", "interrupted commit: $marker")
        if (stamped == 0L || age in 0..7L * 24 * 3600_000L) {
            scope.launch {
                delay(3_000)   // let the speech engine finish initialising
                say(
                    "Last time I pressed '$label' and then stopped. I don't know whether it went " +
                        "through — please check your orders."
                )
            }
        }
    }

    // ---------------------------------------------------------------- emoji

    private fun sendEmoji(name: String) {
        val character = com.vaani.assistant.nlu.Emoji.character(name)
        if (character == null) {
            say("I don't know an emoji called $name.")
            return
        }
        val typed = AccessibilityAgent.get()?.appendText(character) == true
        say(if (typed) "Added it." else "Open the chat first, then ask again.")
    }

    // ---------------------------------------------------------------- memory and notes

    private fun remember(label: String) {
        val auto = LensActivity.lastTitle
        val name = label.ifBlank { auto.orEmpty() }
        if (name.isBlank()) {
            say("Tell me what to call it — try: remember this is my keys.")
            return
        }
        val saved = ObjectMemory(this).remember(
            label = name,
            note = auto?.takeIf { it != name },
            jpeg = LensActivity.lastJpeg
        )
        say(if (saved) "Saved $name." else "I couldn't save that.")
    }

    private fun whereIs(thing: String) {
        val hit = ObjectMemory(this).find(thing)
        if (hit == null) {
            say("I don't have anything saved as $thing yet.")
            return
        }
        val when_ = ago(hit.at)
        val note = hit.note?.let { ", $it" }.orEmpty()
        say("${hit.label} — I saw it $when_$note.")
    }

    private fun takeNote() {
        scope.launch {
            say("Recording a note. Go ahead.")
            delay(400)
            val text = listenOnce()
            if (text.isNullOrBlank()) {
                say("I didn't catch that.")
                return@launch
            }
            val saved = VoiceNotes(this@VoiceService).add(text, null)
            say(if (saved) "Noted." else "I couldn't save that.")
        }
    }

    private fun findNote(query: String) {
        val hits = VoiceNotes(this).search(query)
        if (hits.isEmpty()) {
            say("I don't have a note about $query.")
            return
        }
        val first = hits.first()
        say("${hits.size} note${if (hits.size == 1) "" else "s"}. The closest, ${ago(first.at)}: ${first.text.take(200)}")
    }

    private fun speakNotifications() {
        val items = VaaniNotificationListener.snapshot()
        say(NotificationDigest.summarise(items))
    }

    private fun ago(at: Long): String {
        val minutes = (System.currentTimeMillis() - at) / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes minutes ago"
            minutes < 60 * 24 -> "${minutes / 60} hours ago"
            else -> "${minutes / (60 * 24)} days ago"
        }
    }

    // ---------------------------------------------------------------- reading aloud

    private fun startReading() {
        val a = AccessibilityAgent.get()
        val text = a?.screenText().orEmpty()
        readChunks = ReadAloud.chunks(text)
        readIndex = 0
        if (readChunks.isEmpty()) {
            say("There is nothing on screen to read.")
            return
        }
        val count = readChunks.size
        say("Reading $count part${if (count == 1) "" else "s"}.")
        scope.launch {
            delay(700)
            speakChunk()
        }
    }

    private fun speakChunk() {
        val chunk = readChunks.getOrNull(readIndex) ?: return
        tts.speak(chunk)
    }

    private fun controlReading(action: String) {
        if (readChunks.isEmpty()) {
            say("I'm not reading anything.")
            return
        }
        when (action) {
            "next" -> {
                readIndex = ReadAloud.nextIndex(readIndex, readChunks.size, forward = true)
                speakChunk()
            }
            "back" -> {
                readIndex = ReadAloud.nextIndex(readIndex, readChunks.size, forward = false)
                speakChunk()
            }
            "faster" -> {
                Prefs.speechRate = ReadAloud.stepSpeed(Prefs.speechRate, faster = true)
                speakChunk()
            }
            "slower" -> {
                Prefs.speechRate = ReadAloud.stepSpeed(Prefs.speechRate, faster = false)
                speakChunk()
            }
            "stop" -> {
                tts.stop()
                readChunks = emptyList()
                readIndex = 0
                say("Stopped.")
            }
        }
    }

    // ---------------------------------------------------------------- vault

    private fun fillField(key: String) {
        val normalised = VaultKeys.normalise(key) ?: key
        val value = Vault(this).get(normalised)
        if (value == null) {
            say("I don't have your $normalised saved. Say: save my $normalised as …")
            return
        }
        val typed = AccessibilityAgent.get()?.typeText(value) == true
        say(if (typed) "Filled in your $normalised." else "I couldn't find a field to type into.")
    }

    private fun saveDetail(key: String, value: String) {
        val normalised = VaultKeys.normalise(key) ?: key
        val saved = Vault(this).put(normalised, value)
        say(if (saved) "Saved your $normalised." else "I couldn't save that.")
    }

    // ---------------------------------------------------------------- clipboard

    private fun clipboardText(): String? = runCatching {
        val manager = getSystemService(android.content.ClipboardManager::class.java) ?: return null
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        clip.getItemAt(0).coerceToText(this)?.toString()?.trim()
    }.getOrNull()

    private fun saveClipToInbox() {
        val text = clipboardText()
        if (text.isNullOrBlank()) {
            say("There is nothing on the clipboard to save.")
            return
        }
        val kind = if (text.startsWith("http")) "link" else "note"
        val saved = Inbox(this).add(text, kind)
        say(if (saved) "Saved to your inbox." else "I couldn't save that.")
    }

    private fun speakClipboard() {
        val text = clipboardText()
        if (text.isNullOrBlank()) {
            say("Your clipboard is empty, or Android will not let me read it.")
            return
        }
        say(text.take(200))
    }

    private fun speakInbox() {
        val box = Inbox(this)
        val count = box.count()
        if (count == 0) {
            say("Your inbox is empty.")
            return
        }
        val newest = box.latest()?.text?.take(120).orEmpty()
        val word = if (count == 1) "thing" else "things"
        say("$count $word in your inbox. The newest is: $newest")
    }

    // ---------------------------------------------------------------- dictation

    /**
     * Sentence dictation. Each utterance is rendered into written text and appended to the
     * focused field, then the microphone reopens — no wake word, no per-word stepping.
     */
    private fun startDictation() {
        if (dictating) return
        val agent = AccessibilityAgent.get()
        if (agent == null) {
            say(Reply.say("need_screen", lastMix))
            return
        }
        if (!agent.hasEditableField()) {
            say(Reply.say("dictation_no_field", lastMix))
            return
        }
        agent.focusFirstEditable()
        dictating = true
        lastDictationAt = System.currentTimeMillis()
        followUpOpen = true
        say(Reply.say("dictation_started", lastMix))
        Vlog.i("Dict", "session started")
        scope.launch {
            // Close the session if nothing has been said for a while.
            while (dictating) {
                delay(5_000)
                val idle = System.currentTimeMillis() - lastDictationAt
                if (idle > Prefs.dictationIdleMs) {
                    stopDictation(timedOut = true)
                    break
                }
            }
        }
    }

    private fun stopDictation(timedOut: Boolean = false) {
        if (!dictating) return
        dictating = false
        followUpOpen = false
        Vlog.i("Dict", if (timedOut) "session ended after idle" else "session ended by user")
        say(Reply.say(if (timedOut) "dictation_timeout" else "dictation_stopped", lastMix))
        wake.restart()
    }

    private fun handleDictation(text: String) {
        val n = Text.norm(text)
        if (isDictationExit(n)) {
            stopDictation()
            return
        }
        lastDictationAt = System.currentTimeMillis()

        val sentence = Punctuation.render(text, Prefs.autoPunctuation)
        if (sentence.isBlank()) {
            wake.restart()
            return
        }
        val ok = AccessibilityAgent.get()?.appendText(sentence) ?: false
        if (!ok) {
            say(Reply.say("dictation_failed", lastMix))
            stopDictation()
            return
        }
        Vlog.i("Dict", sentence)
        wake.restart()
    }

    private fun isDictationExit(n: String): Boolean =
        n == "stop typing" || n == "stop dictating" || n == "exit dictation" ||
            n == "end dictation" || n == "finish dictation" || n == "bas karo" ||
            n == "likhna band" || n == "band karo" || n == "stop" ||
            n.contains("stop typing") || n.contains("stop dictating") ||
            n.contains("band karo likhna") || n.contains("likhna band karo")

    // ---------------------------------------------------------------- the pipeline

    private fun handle(text: String) {
        scope.launch {
            if (busy) {
                say(Reply.say("busy", lastMix))
                return@launch
            }
            busy = true
            try {
                lastMix = LanguageDetector.detect(text)
                Vlog.i("CMD", "[${lastMix?.primary}${if (lastMix?.mixed == true) "+mixed" else ""}] $text")
                val plan = buildPlan(text)
                if (plan != null) {
                    val ok = executor.run(plan)
                    if (ok) openFollowUp()
                }
            } catch (t: Throwable) {
                Vlog.e("Svc", "pipeline failed: ${t.message}")
                say(Reply.say("error", lastMix))
            } finally {
                busy = false
                // Hands-free listening resumes only once we are quiet again.
                wake.restart()
            }
        }
    }

    /** After a command, stay open for "and then ..." without repeating the wake word. */
    private fun openFollowUp() {
        if (!Prefs.followUpEnabled) return
        followUpOpen = true
        wake.armFollowUp(Prefs.followUpMs)
        scope.launch {
            delay(Prefs.followUpMs)
            followUpOpen = false
        }
    }

    private suspend fun buildPlan(text: String): Plan? {
        recipes.match(text)?.let { recipe ->
            Vlog.i("Svc", "recipe: ${recipe.name}")
            return recipes.plan(recipe)
        }

        var cmd = parser.parse(text)
        if (cmd is Command.Repeat) {
            val last = lastCommand
            if (last == null) {
                say(Reply.say("nothing_repeat", lastMix))
                return null
            }
            cmd = last
        }
        if (cmd is Command.Unknown) cmd = rescue(cmd.raw)

        when (cmd) {
            is Command.Dictate -> {
                startDictation()
                return null
            }
            is Command.StopDictation -> {
                stopDictation()
                return null
            }
            else -> Unit
        }
        if (cmd !is Command.Repeat) lastCommand = cmd

        updateLanguageHint()
        return planner.plan(cmd)
    }

    /** Last resort for utterances the grammar missed: bare app name, then the optional LLM. */
    private suspend fun rescue(raw: String): Command {
        apps.resolve(raw)?.let { return Command.OpenApp(it.label) }

        if (Prefs.llmEnabled && llm.available()) {
            val screen = AccessibilityAgent.get()?.describeScreen() ?: ""
            val fromModel = llm.parse(raw, screen)
            if (fromModel != null && fromModel !is Command.Unknown) {
                Vlog.i("LLM", "planned: $fromModel")
                return fromModel
            }
        }
        return Command.Unknown(raw)
    }

    /**
     * Nudge the recogniser's language hint towards what the user actually speaks. Code-mixed
     * Indian speech is mostly Latin-script, so en-IN stays the default; a clearly Tamil or
     * Bengali utterance switches the hint so the next round transcribes in that script.
     */
    private fun updateLanguageHint() {
        if (!Prefs.autoLanguage) return
        val mix = lastMix ?: return
        val tag = when {
            mix.confidence < 0.6f -> "en-IN"
            mix.primary == "en" -> "en-IN"
            else -> mix.tag()
        }
        if (tag != Prefs.asrLanguage) {
            Vlog.i("Lang", "recogniser hint -> $tag")
            Prefs.asrLanguage = tag
        }
    }

    // ---------------------------------------------------------------- plumbing

    private fun say(text: String) {
        if (text.isBlank()) return
        Vlog.i("Say", text)
        if (Prefs.speakReplies) tts.speak(text, lastMix?.tag() ?: "en-IN")
    }

    private fun showBubble(on: Boolean) {
        if (on) {
            if (bubble == null) bubble = OverlayBubble(this) { beginListening() }
            bubble?.show()
        } else {
            bubble?.hide()
            bubble = null
        }
    }

    private fun startMemoryTicker() {
        memoryTicker?.cancel()
        memoryTicker = scope.launch {
            while (isActive) {
                delay(60_000)
                llm.trimIfNeeded()
            }
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun goForeground() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val listen = PendingIntent.getService(
            this, 1, Intent(this, VoiceService::class.java).setAction(ACTION_LISTEN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Listen", listen)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // If the microphone permission was revoked, fall back to an untyped foreground
            // service instead of crashing the whole assistant.
            val ok = runCatching {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            }.isSuccess
            if (!ok) startForeground(NOTIF_ID, notification)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }
}
