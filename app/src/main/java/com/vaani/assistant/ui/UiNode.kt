package com.vaani.assistant.ui

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** One visible element on screen, flattened out of the accessibility tree. */
class UiNode(
    val node: AccessibilityNodeInfo,
    override val label: String,
    val className: String,
    val bounds: Rect,
    override val clickable: Boolean,
    override val editable: Boolean,
    val scrollable: Boolean,
    val depth: Int
) : Box {

    override val left: Int get() = bounds.left
    override val top: Int get() = bounds.top
    override val right: Int get() = bounds.right
    override val bottom: Int get() = bounds.bottom
    val centreX: Float get() = bounds.exactCenterX()
    val centreY: Float get() = bounds.exactCenterY()

    override fun toString(): String = "$className[${label.take(40)}]"
}
