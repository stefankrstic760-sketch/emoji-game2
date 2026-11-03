package com.example.pongbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/** Holds one continuous finger-drag on the paddle and keeps extending it toward new x targets. */
class BotAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: BotAccessibilityService? = null }

    private val h = Handler(Looper.getMainLooper())
    private var stroke: StrokeDescription? = null
    private var endX = -1f
    private var pendingX = -1f
    private var y = 0f
    private var busy = false

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(i: android.content.Intent?): Boolean { instance = null; return super.onUnbind(i) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun moveTo(x: Float, yPos: Float) = h.post { pendingX = x; y = yPos; if (!busy) next() }

    private fun next() {
        val tx = pendingX
        if (tx < 0) return
        pendingX = -1f
        val path = Path()
        val prev = stroke
        val s: StrokeDescription
        if (prev == null) {
            path.moveTo(tx, y); path.lineTo(tx + 1, y)
            s = StrokeDescription(path, 0, 40, true)
            endX = tx + 1
        } else {
            path.moveTo(endX, y); path.lineTo(tx, y)
            s = prev.continueStroke(path, 0, 40, true)
            endX = tx
        }
        busy = true
        val ok = dispatchGesture(GestureDescription.Builder().addStroke(s).build(),
            object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { stroke = s; busy = false; if (pendingX >= 0) next() }
                override fun onCancelled(g: GestureDescription?) { stroke = null; busy = false }
            }, null)
        if (!ok) { busy = false; stroke = null }
    }

    /** Lift the finger. */
    fun release() = h.post {
        val prev = stroke ?: return@post
        val path = Path().apply { moveTo(endX, y); lineTo(endX, y) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(prev.continueStroke(path, 0, 10, false)).build(), null, null)
        stroke = null; pendingX = -1f
    }
}
