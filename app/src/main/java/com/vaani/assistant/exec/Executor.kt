package com.vaani.assistant.exec

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.telephony.SmsManager
import com.vaani.assistant.core.Prefs
import com.vaani.assistant.core.Text
import com.vaani.assistant.core.Vlog
import com.vaani.assistant.nlu.LanguageDetector
import com.vaani.assistant.nlu.Ordinals
import com.vaani.assistant.ui.AccessibilityAgent
import com.vaani.assistant.ui.ScreenDescriber
import com.vaani.assistant.ui.UiNode
import com.vaani.assistant.voice.Reply
import kotlinx.coroutines.delay

/**
 * Runs a [Plan] step by step. Every tap and launch passes through [SafetyGuard] first, and every
 * step that needs the screen re-checks that the screen is not a payment flow before acting.
 */
class Executor(
    private val ctx: Context,
    private val speak: (String) -> Unit,
    private val media: MediaControl
) {

    /** Set by the voice service: asks the user to confirm a destructive step out loud. */
    var confirmHandler: (suspend (String) -> Boolean)? = null

    /**
     * Set by the voice service: shows the order summary and waits for a human yes. Returns false
     * if the user said no, or if no gate is available at all.
     */
    var commitHandler: (suspend (ScreenSummary) -> Boolean)? = null

    /** Set when an authorised step ends the plan on purpose, so it is not reported as a failure. */
    private var haltRequested = false

    /**
     * One commit at a time. A double tap, a re-entrant plan or a step retried after a timeout must
     * never press "Place order" twice — so the whole gate is single-entry.
     */
    private val commitLock = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Language of the utterance being executed, so failures are spoken in the same mix. */
    var mixProvider: () -> LanguageDetector.LanguageMix? = { null }

    private fun say(event: String, vararg args: String) {
        speak(Reply.say(event, mixProvider(), *args))
    }

    private val a11y: AccessibilityAgent? get() = AccessibilityAgent.get()

    suspend fun run(plan: Plan): Boolean {
        if (plan.steps.isEmpty()) {
            speak(plan.say ?: "I didn't understand that.")
            return false
        }
        plan.say?.let { speak(it) }
        Obstacle.reset()
        for (step in plan.steps) {
            val ok = execute(step)
            if (haltRequested) {
                haltRequested = false
                return true          // handed over to the human on purpose
            }
            if (!ok) return false
        }
        return true
    }

    private suspend fun execute(step: Step): Boolean {
        return when (step) {

        is Step.Launch -> {
            val guard = SafetyGuard.forApp(step.pkg)
            if (!guard.allowed) return refuse(guard)
            launch(step.pkg, step.uri)
        }

        is Step.OpenUri -> {
            step.pkg?.let {
                val guard = SafetyGuard.forApp(it)
                if (!guard.allowed) return refuse(guard)
            }
            launch(step.pkg, step.uri)
        }

        is Step.Raw -> {
            runCatching { ctx.startActivity(step.intent) }.fold(
                onSuccess = { true },
                onFailure = {
                    Vlog.e("Exec", "intent failed: ${it.message}")
                    say("open_fail")
                    false
                }
            )
        }

        is Step.WaitFor -> {
            val a = a11y ?: return needAccessibility()
            val found = waitForLabel(a, step.label, step.timeoutMs)
            if (!found) say("waiting", step.label)
            found
        }

        is Step.Tap -> tap(step.label, step.timeoutMs)

        is Step.FocusSearch -> {
            val a = a11y ?: return needAccessibility()
            val deadline = System.currentTimeMillis() + step.timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val nodes = a.snapshot()
                val target = nodes.firstOrNull { it.editable }
                    ?: nodes.firstOrNull { Text.norm(it.label).contains("search") }
                if (target != null) {
                    a.clickNode(target)
                    delay(400)
                    return true
                }
                delay(200)
            }
            say("search_box")
            false
        }

        is Step.Type -> {
            val a = a11y ?: return needAccessibility()
            val screenGuard = SafetyGuard.forScreen(a.snapshot())
            if (screenGuard.verdict == SafetyGuard.Verdict.DENY) return refuse(screenGuard)
            delay(250)
            val ok = a.typeText(step.text)
            if (!ok) {
                say("type_fail")
                return false
            }
            if (step.submit) {
                delay(400)
                a.submit()
            }
            true
        }

        is Step.Scroll -> {
            val a = a11y ?: return needAccessibility()
            a.scroll(step.forward)
        }

        is Step.Pause -> {
            delay(step.ms)
            true
        }

        is Step.Media -> {
            if (media.apply(step.action)) true
            else {
                say("music_fail")
                false
            }
        }

        is Step.Volume -> media.volume(step.up)

        is Step.Flashlight -> setFlashlight(step.on)

        is Step.Dnd -> setDnd(step.on)

        is Step.SendSms -> sendSms(step.number, step.text)

        is Step.Say -> {
            speak(step.text)
            true
        }

        is Step.Confirm -> {
            val handler = confirmHandler
            if (handler == null) {
                say("confirm_needed")
                false
            } else handler.invoke(step.question)
        }

        Step.Back -> a11y?.globalBack() ?: needAccessibility()
        Step.Home -> a11y?.globalHome() ?: needAccessibility()
        Step.Recents -> a11y?.globalRecents() ?: needAccessibility()

        Step.ReadScreen -> {
            val a = a11y ?: return needAccessibility()
            val text = ScreenDescriber.describe(a.snapshot(), appLabelOf(a.foregroundPackage()))
            if (text.isBlank()) say("screen_empty")
            else speak(text.take(600))
            true
        }
        }
    }

    // ------------------------------------------------------------------ pieces

    private suspend fun tap(label: String, timeoutMs: Long): Boolean {
        val a = a11y ?: return needAccessibility()
        val screenGuard = SafetyGuard.forScreen(a.snapshot())
        if (screenGuard.verdict == SafetyGuard.Verdict.DENY) return refuse(screenGuard)

        // "tap the third one" is resolved against what is actually on screen, in reading order.
        resolveOrdinal(label, a)?.let { target ->
            val guard = SafetyGuard.forText(target.label)
            when (guard.verdict) {
                SafetyGuard.Verdict.DENY -> return refuse(guard, target.label)
                SafetyGuard.Verdict.AUTHORISE -> return authorise(target.label, a)
                SafetyGuard.Verdict.CONFIRM -> {
                    val ok = confirmHandler?.invoke("${target.label} looks destructive. Go ahead?") ?: false
                    if (!ok) {
                        say("cancelled")
                        return false
                    }
                }
                SafetyGuard.Verdict.ALLOW -> Unit
            }
            Vlog.i("Exec", "ordinal '$label' -> '${target.label}'")
            if (a.clickNode(target)) {
                delay(350)
                return true
            }
        }

        if (!waitForLabel(a, label, timeoutMs)) {
            // Most misses are a banner or upsell sitting on top of the target.
            if (clearObstacle(a) && waitForLabel(a, label, 3000)) {
                Vlog.i("Exec", "'$label' visible after clearing an obstacle")
            } else {
                return guide(label, a)
            }
        }
        val guard = SafetyGuard.forText(label)
        when (guard.verdict) {
            SafetyGuard.Verdict.DENY -> return refuse(guard, label)
            SafetyGuard.Verdict.AUTHORISE -> return authorise(label, a)
            SafetyGuard.Verdict.CONFIRM -> {
                val ok = confirmHandler?.invoke("$label looks destructive. Should I go ahead?") ?: false
                if (!ok) {
                    say("cancelled")
                    return false
                }
            }
            SafetyGuard.Verdict.ALLOW -> Unit
        }
        repeat(2) {
            if (a.clickText(label)) {
                delay(350)
                return true
            }
            delay(400)
        }
        say("tap_fail", label)
        return false
    }

    /**
     * The money gate. Summarise the screen, ask, and on a yes perform exactly one tap and stop.
     * Vaani never walks past this point: no PIN, no OTP, no second tap.
     */
    private suspend fun authorise(label: String, a: AccessibilityAgent): Boolean {
        val handler = commitHandler
        if (handler == null) {
            say("commit_unavailable")
            return false
        }
        // Second caller loses: this is the double-tap guard.
        if (!commitLock.compareAndSet(false, true)) {
            Vlog.i("Gate", "commit already in flight, ignoring '$label'")
            return false
        }
        try {
            val appLabel = appLabelOf(a.foregroundPackage())
            val summary = ScreenSummary.from(a.snapshot(), appLabel)
            Vlog.i("Gate", "authorise '$label' · ${summary.bullets.size} bullets")

            val approved = handler.invoke(summary)
            if (!approved) {
                say("cancelled")
                Vlog.i("Gate", "user declined at the order summary")
                return false
            }

            // Journal the intent before the tap. If the phone dies mid-press, the next start knows
            // to tell the user to check their orders rather than assume nothing happened.
            Prefs.pendingCommit = "$label|$appLabel|${System.currentTimeMillis()}"

            val tapped = a.clickText(label)
            delay(500)
            if (!tapped) {
                Prefs.pendingCommit = null
                say("tap_fail", label)
                return false
            }
            Prefs.pendingCommit = null
            haltRequested = true
            say("handover")
            Vlog.i("Gate", "one tap performed, plan halted for manual payment")
            return true
        } finally {
            commitLock.set(false)
        }
    }

    private fun appLabelOf(pkg: String?): String {
        if (pkg == null) return "this app"
        return runCatching {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    private suspend fun waitForLabel(a: AccessibilityAgent, label: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (a.findNode(label) != null) return true
            delay(180)
        }
        return false
    }

    private fun launch(pkg: String?, uri: String?): Boolean {
        val pm = ctx.packageManager
        val primary: Intent? = when {
            uri != null && pkg != null -> Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            uri != null -> Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            pkg != null -> pm.getLaunchIntentForPackage(pkg)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            else -> null
        }
        if (primary == null) {
            say("open_fail")
            return false
        }
        runCatching { ctx.startActivity(primary) }.onSuccess { return true }

        // Deep link refused by the app: fall back to a plain launch.
        if (pkg != null) {
            val plain = pm.getLaunchIntentForPackage(pkg)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (plain != null) {
                runCatching { ctx.startActivity(plain) }.onSuccess { return true }
            }
        }
        say("open_fail")
        return false
    }

    private fun setFlashlight(on: Boolean): Boolean = runCatching {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull { cam ->
            cm.getCameraCharacteristics(cam).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return false
        cm.setTorchMode(id, on)
        true
    }.getOrElse {
        Vlog.e("Exec", "torch failed: ${it.message}")
        say("flashlight_fail")
        false
    }

    private fun setDnd(on: Boolean): Boolean = runCatching {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            say("dnd_access")
            return false
        }
        nm.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        true
    }.getOrDefault(false)

    private fun sendSms(number: String, text: String): Boolean = runCatching {
        val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        sms.sendTextMessage(number, null, text, null, null)
        Vlog.i("Exec", "sms sent to $number")
        true
    }.getOrElse {
        Vlog.e("Exec", "sms failed: ${it.message}")
        say("sms_fail")
        false
    }

    private fun refuse(decision: SafetyGuard.Decision, label: String? = null): Boolean {
        Vlog.i("Guard", "blocked: ${decision.reason}")
        when (decision.reason) {
            "piracy", "piracy app" -> say("blocked_piracy")
            else -> say("blocked_payment")
        }
        // Then point at it, so the person knows exactly what to press themselves.
        if (label != null) {
            val agent = a11y
            if (agent != null) {
                say(Guidance.where(agent.snapshot(), label, screenWidth(), screenHeight()))
            }
        }
        return false
    }

    /**
     * Couldn't find the control. Offer the closest thing that means the same, and if that is not
     * taken, say where the original lives.
     */
    /**
     * Turns "the third one" into a node. Deliberately strict about what counts as an ordinal: a
     * button labelled "Add 2 to cart" must not be read as "the second thing".
     */
    private fun resolveOrdinal(label: String, a: AccessibilityAgent): UiNode? {
        val looksOrdinal = ORDINAL_PHRASE.containsMatchIn(label) ||
            BARE_NUMBER.matches(label)
        if (!looksOrdinal) return null

        val items = a.snapshot()
            .filter { it.clickable && it.label.isNotBlank() }
            .sortedBy { it.top }
        if (items.isEmpty()) return null

        val index = Ordinals.parse(label)
        val resolved = when {
            Ordinals.isLast(label) -> items.lastIndex
            Ordinals.countsFromBottom(label) -> items.size - 1 - (index ?: 0)
            else -> index ?: 0
        }
        return items.getOrNull(resolved)
    }

    private suspend fun guide(label: String, a: AccessibilityAgent): Boolean {
        val nodes = a.snapshot()
        val alternatives = Guidance.similar(nodes, label)

        if (alternatives.isNotEmpty()) {
            val candidate = alternatives.first()
            val question = Reply.say("not_found_offer", mixProvider(), label, candidate)
            val accepted = confirmHandler?.invoke(question) ?: false
            if (accepted) {
                val guard = SafetyGuard.forText(candidate)
                if (guard.verdict == SafetyGuard.Verdict.DENY) return refuse(guard, candidate)
                if (a.clickText(candidate)) {
                    delay(350)
                    Vlog.i("Guide", "tapped alternative '$candidate' for '$label'")
                    return true
                }
            }
        }

        say(Reply.say("not_found_direction", mixProvider(), label,
            Guidance.where(nodes, label, screenWidth(), screenHeight())))
        return false
    }

    /** Dismiss one thing that is in the way. Returns true if something was pressed. */
    private suspend fun clearObstacle(a: AccessibilityAgent): Boolean {
        val target = Obstacle.findDismisser(a.snapshot()) ?: return false
        val pressed = a.clickNode(target)
        if (pressed) {
            Obstacle.noteCleared(target.label)
            delay(600)
        }
        return pressed
    }

    private fun screenWidth(): Int = ctx.resources.displayMetrics.widthPixels

    private fun screenHeight(): Int = ctx.resources.displayMetrics.heightPixels

    private fun needAccessibility(): Boolean {
        say("need_screen")
        // Point the way literally: open the accessibility list the user has to touch.
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return false
    }

    private companion object {
        /**
         * Words that make a phrase an ordinal. A button labelled "Add 2 to cart" must not be read
         * as "the second thing", so a bare number only counts when it is the whole phrase.
         */
        val ORDINAL_PHRASE = Regex(
            "(?i)\\b(one|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|last|bottom|top|" +
                "\\d{1,2}(st|nd|rd|th))\\b"
        )
        val BARE_NUMBER = Regex("^\\s*(?:the\\s+|number\\s+)?\\d{1,2}\\s*(?:one|item|option)?\\s*$")
    }
}
