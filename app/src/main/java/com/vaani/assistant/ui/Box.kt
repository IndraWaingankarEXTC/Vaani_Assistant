package com.vaani.assistant.ui

/**
 * A labelled rectangle on screen.
 *
 * [UiNode] is the real thing, but it wraps [android.view.accessibility.AccessibilityNodeInfo],
 * which cannot exist outside a running device. The heuristics that read a screen — the order
 * summary, the "it's just under X" pointer, the payment check — only ever need a label and
 * bounds, so they take this instead and stay unit-testable on a plain JVM.
 */
interface Box {
    val label: String
    val left: Int
    val top: Int
    val right: Int
    val bottom: Int
    val clickable: Boolean
    val editable: Boolean
}
