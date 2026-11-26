package com.vaani.assistant.ui

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.vaani.assistant.core.Text
import com.vaani.assistant.core.Vlog

/**
 * The "hands" of the assistant: reads the accessibility tree and performs taps, typing,
 * scrolling and global actions. Everything here is generic, so it works in any app, including
 * ones that ship new UI next week.
 */
class AccessibilityAgent : AccessibilityService() {

    companion object {
        @Volatile
        private var ref: AccessibilityAgent? = null

        fun get(): AccessibilityAgent? = ref
        fun ready(): Boolean = ref != null

        private const val MAX_NODES = 400
        private const val MAX_DEPTH = 30
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ref = this
        Vlog.i("A11y", "screen control connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        ref = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (ref === this) ref = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- reading

    fun snapshot(limit: Int = MAX_NODES): List<UiNode> {
        val root = rootInActiveWindow ?: return emptyList()
        val out = ArrayList<UiNode>(minOf(limit, 128))
        walk(root, 0, out, limit)
        return out
    }

    private fun walk(node: AccessibilityNodeInfo, depth: Int, out: MutableList<UiNode>, limit: Int) {
        if (out.size >= limit || depth > MAX_DEPTH) return
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val visible = node.isVisibleToUser && bounds.width() > 0 && bounds.height() > 0
        if (visible) {
            val label = node.text?.toString()?.trim().orEmpty()
                .ifEmpty { node.contentDescription?.toString()?.trim().orEmpty() }
                .ifEmpty { node.hintText?.toString()?.trim().orEmpty() }
            if (label.isNotEmpty()) {
                out.add(
                    UiNode(
                        node = node,
                        label = label,
                        className = node.className?.toString().orEmpty(),
                        bounds = Rect(bounds),
                        clickable = node.isClickable,
                        editable = node.isEditable,
                        scrollable = node.isScrollable,
                        depth = depth
                    )
                )
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, depth + 1, out, limit)
            if (out.size >= limit) return
        }
    }

    fun foregroundPackage(): String? = rootInActiveWindow?.packageName?.toString()

    fun screenText(): String =
        snapshot().joinToString(" | ") { it.label }.ifEmpty { "" }

    /** Compact description of the screen for the optional LLM planner. */
    fun describeScreen(maxItems: Int = 40): String =
        snapshot(maxItems * 3).take(maxItems).joinToString("\n") { n ->
            val flags = buildString {
                if (n.clickable) append("[tap]")
                if (n.editable) append("[input]")
                if (n.scrollable) append("[scroll]")
            }
            "${n.label.take(60)} $flags"
        }

    // ---------------------------------------------------------------- acting

    fun findNode(target: String): UiNode? {
        val t = Text.norm(target)
        if (t.isEmpty()) return null
        val nodes = snapshot().filter { it.label.isNotBlank() }

        nodes.firstOrNull { Text.norm(it.label) == t }?.let { return it }

        val contains = nodes.filter { n ->
            val l = Text.norm(n.label)
            l.contains(t) || (t.length >= 4 && t.contains(l))
        }
        if (contains.isNotEmpty()) return contains.minByOrNull { it.label.length }

        val best = Text.bestMatch(t, nodes.map { it.label }, 0.72) ?: return null
        return nodes.firstOrNull { it.label == best }
    }

    fun clickText(target: String): Boolean {
        val hit = findNode(target)
        if (hit == null) {
            Vlog.i("A11y", "no node matched '$target'")
            return false
        }
        return clickNode(hit)
    }

    fun clickNode(hit: UiNode): Boolean {
        var cur: AccessibilityNodeInfo? = hit.node
        var guard = 0
        while (cur != null && guard++ < 8) {
            if (cur.isClickable && cur.isEnabled) {
                if (cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                break
            }
            cur = cur.parent
        }
        return tap(hit.centreX, hit.centreY)
    }

    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 260): Boolean {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val target = if (focused != null && focused.isEditable) focused else firstEditable(root)
        if (target == null) {
            Vlog.i("A11y", "no editable field on screen")
            return false
        }
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Vlog.i("A11y", "type '${text.take(30)}' -> $ok")
        return ok
    }

    /** Is there anywhere to type on this screen? */
    fun hasEditableField(): Boolean {
        val root = rootInActiveWindow ?: return false
        return firstEditable(root) != null
    }

    /** Put the cursor in the first text field, for when dictation starts with nothing focused. */
    fun focusFirstEditable(): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = firstEditable(root) ?: return false
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return true
    }

    /** Text already in the focused field, so dictation can add to it instead of replacing it. */
    fun focusedFieldText(): String? {
        val root = rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val target = if (focused != null && focused.isEditable) focused else firstEditable(root)
        return target?.text?.toString()
    }

    /**
     * Add a sentence to whatever is already in the field and leave the cursor at the end.
     * ACTION_SET_TEXT replaces the whole field, so the existing text is read back and rebuilt.
     */
    fun appendText(sentence: String): Boolean {
        if (sentence.isEmpty()) return false
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val target = if (focused != null && focused.isEditable) focused else firstEditable(root)
        if (target == null) {
            Vlog.i("A11y", "no editable field for dictation")
            return false
        }
        val readable = target.text?.toString()

        // A password field reports no text. Rebuilding the field from "" would wipe it, so refuse.
        if (readable == null && target.isPassword) {
            Vlog.i("A11y", "refusing to type into a password field")
            return false
        }

        // Some fields hide their value (null text) while still being editable. Pasting at the
        // cursor adds to the field without needing to read it, so nothing is lost.
        if (readable == null) {
            val pasted = pasteAtCursor(target, sentence)
            Vlog.i("A11y", "unreadable field, paste fallback -> $pasted")
            return pasted
        }

        val existing = readable
        val separator = when {
            existing.isEmpty() -> ""
            sentence.startsWith("\n") -> ""
            existing.endsWith(" ") || existing.endsWith("\n") -> ""
            else -> " "
        }
        val merged = existing + separator + sentence
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, merged)
        }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok) {
            val selection = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, merged.length)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, merged.length)
            }
            runCatching { target.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection) }
        }
        Vlog.i("A11y", "append ${sentence.length} chars -> $ok")
        return ok
    }

    /** Append without reading the field: put the text on the clipboard and paste at the cursor. */
    private fun pasteAtCursor(node: AccessibilityNodeInfo, sentence: String): Boolean {
        val clipboard = ctx()?.getSystemService(android.content.ClipboardManager::class.java)
            ?: return false
        val clip = android.content.ClipData.newPlainText("vaani", sentence)
        runCatching { clipboard.setPrimaryClip(clip) }
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun ctx(): android.content.Context? = applicationContext

    private fun firstEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 600) {
            val n = queue.removeFirst()
            if (n.isEditable && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    /** Send the IME "enter"/"go" action, which is what a search box actually listens for. */
    fun submit(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        // The IME "enter" action is an AccessibilityAction added in API 30; there is no
        // AccessibilityNodeInfo.ACTION_IME_ENTER int constant.
        if (focused != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val imeEnter = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER
            if (focused.performAction(imeEnter.id, null)) return true
        }
        // Fall back to a visible search / go button.
        return clickText("search") || clickText("go") || clickText("submit")
    }

    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        findScrollable(root)?.let { node ->
            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if (node.performAction(action)) return true
        }
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        return if (forward) {
            swipe(w / 2f, h * 0.72f, w / 2f, h * 0.28f)
        } else {
            swipe(w / 2f, h * 0.28f, w / 2f, h * 0.72f)
        }
    }

    private fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 600) {
            val n = queue.removeFirst()
            if (n.isScrollable && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    fun globalBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun globalHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun globalRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
}
