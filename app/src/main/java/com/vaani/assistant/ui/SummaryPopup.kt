package com.vaani.assistant.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.vaani.assistant.core.Vlog
import com.vaani.assistant.exec.ScreenSummary

/**
 * The card that stands between Vaani and your money.
 *
 * It floats over whatever app is on screen (an overlay, because Android will not let a background
 * service start an activity over someone else's app), shows the short bullets, and waits. It
 * never times out into a yes: silence is a no.
 */
class SummaryPopup(private val ctx: Context) {

    companion object {
        private const val TIMEOUT_MS = 90_000L
    }

    private var root: View? = null
    private var windowManager: WindowManager? = null
    private var timeout: Runnable? = null
    private val main = Handler(Looper.getMainLooper())
    private var decided = false

    /** Returns false when it could not be displayed (no overlay permission). */
    fun show(summary: ScreenSummary, onDecision: (Boolean) -> Unit): Boolean {
        dismiss()
        if (!Settings.canDrawOverlays(ctx)) {
            Vlog.e("Popup", "no overlay permission, falling back to voice only")
            return false
        }
        val wm = ctx.getSystemService(WindowManager::class.java) ?: return false
        decided = false

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#F21B1B3A"))
                setStroke(dp(1), Color.parseColor("#7CE3C1"))
            }
        }

        card.addView(TextView(ctx).apply {
            text = "Confirm before I go ahead"
            setTextColor(Color.parseColor("#7CE3C1"))
            textSize = 13f
            letterSpacing = 0.06f
        })

        card.addView(TextView(ctx).apply {
            text = summary.appLabel
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, dp(6), 0, dp(10))
        })

        card.addView(TextView(ctx).apply {
            text = summary.bullets.joinToString("\n") { "•  $it" }
            setTextColor(Color.parseColor("#E6E6F0"))
            textSize = 14f
            setLineSpacing(dp(4).toFloat(), 1f)
        })

        card.addView(TextView(ctx).apply {
            text = "This is what I read on the screen. Check it — then pay yourself."
            setTextColor(Color.parseColor("#9A9AB0"))
            textSize = 11f
            setPadding(0, dp(12), 0, dp(12))
        })

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        row.addView(Button(ctx).apply {
            text = "Cancel"
            setOnClickListener { finish(false, onDecision) }
        })
        row.addView(Button(ctx).apply {
            text = "Looks good"
            setOnClickListener { finish(true, onDecision) }
        })
        card.addView(row)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(80)
            horizontalMargin = 0.06f
        }

        runCatching { wm.addView(card, params) }.onFailure {
            Vlog.e("Popup", "addView failed: ${it.message}")
            return false
        }
        windowManager = wm
        root = card

        // Silence is not consent: no answer in 90 s means no.
        val t = Runnable {
            Vlog.i("Popup", "timed out, treating as no")
            finish(false, onDecision)
        }
        timeout = t
        main.postDelayed(t, TIMEOUT_MS)
        return true
    }

    private fun finish(decision: Boolean, onDecision: (Boolean) -> Unit) {
        if (decided) return
        decided = true
        dismiss()
        main.post { onDecision(decision) }
    }

    fun dismiss() {
        timeout?.let { main.removeCallbacks(it) }
        timeout = null
        val v = root ?: return
        runCatching { windowManager?.removeView(v) }
        root = null
        windowManager = null
    }

    private fun dp(value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()
}
