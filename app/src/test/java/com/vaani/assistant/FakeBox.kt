package com.vaani.assistant

import com.vaani.assistant.ui.Box

/** A screen element with no Android behind it, for testing the screen heuristics. */
data class FakeBox(
    override val label: String,
    override val left: Int,
    override val top: Int,
    override val right: Int,
    override val bottom: Int,
    override val clickable: Boolean = true,
    override val editable: Boolean = false
) : Box
