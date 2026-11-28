package com.vaani.assistant.voice

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.vaani.assistant.R
import com.vaani.assistant.core.Vlog
import kotlin.math.abs

/** Draggable floating mic. Tap to talk, drag to move. */
class OverlayBubble(private val ctx: Context, private val onTap: () -> Unit) {

    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null

    fun show() {
        if (view != null) return
        if (!Settings.canDrawOverlays(ctx)) {
            Vlog.e("Bubble", "overlay permission missing")
            return
        }
        val wm = ctx.getSystemService(WindowManager::class.java) ?: return
        val v = LayoutInflater.from(ctx).inflate(R.layout.bubble, null)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 12
            y = 420
        }
        attachDrag(v, lp, wm)
        runCatching { wm.addView(v, lp) }.onFailure {
            Vlog.e("Bubble", "addView failed: ${it.message}")
            return
        }
        windowManager = wm
        params = lp
        view = v
    }

    private fun attachDrag(v: View, lp: WindowManager.LayoutParams, wm: WindowManager) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        v.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(v, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onTap()
                    true
                }
                else -> false
            }
        }
    }

    fun hide() {
        val v = view ?: return
        runCatching { windowManager?.removeView(v) }
        view = null
        params = null
        windowManager = null
    }
}
