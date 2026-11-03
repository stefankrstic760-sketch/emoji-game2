package com.example.pongbot

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.WindowManager
import kotlin.math.abs

/** Captures the screen (~60 fps), finds the moving ball, predicts where it lands, and moves the paddle. */
class CaptureService : Service() {
    private var proj: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val thread = HandlerThread("pong-vision").also { it.start() }
    private val handler = Handler(thread.looper)

    private var prev: IntArray? = null
    private var lastCx = 0f; private var lastCy = 0f; private var lastT = 0L
    private var vx = 0f; private var vy = 0f

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("bot", "Bot", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "bot").setContentTitle("Emoji Pong Bot running")
            .setSmallIcon(android.R.drawable.ic_media_play).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        val code = intent?.getIntExtra("code", 0) ?: 0
        @Suppress("DEPRECATION") val data = intent?.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        proj = mpm.getMediaProjection(code, data)
        proj!!.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, handler)

        val b = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        val sw = b.width(); val sh = b.height()
        val vw = sw / Config.SCALE; val vh = sh / Config.SCALE
        reader = ImageReader.newInstance(vw, vh, PixelFormat.RGBA_8888, 2)
        vd = proj!!.createVirtualDisplay("pong", vw, vh, resources.displayMetrics.densityDpi / Config.SCALE,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler)
        reader!!.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try { onFrame(img, sw, sh) } finally { img.close() }
        }, handler)
        return START_NOT_STICKY
    }

    private fun onFrame(img: Image, sw: Int, sh: Int) {
        val w = img.width; val h = img.height
        val p = img.planes[0]; val buf = p.buffer; val rs = p.rowStride; val ps = p.pixelStride
        val cur = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * rs + x * ps
            val r = buf.get(i).toInt() and 0xff; val g = buf.get(i + 1).toInt() and 0xff; val bl = buf.get(i + 2).toInt() and 0xff
            cur[y * w + x] = (r * 3 + g * 6 + bl) / 10
        }
        val old = prev; prev = cur
        if (old == null) return

        // Search band: below the score area, above the paddle.
        val py = Config.paddleY * h
        val y0 = (h * 0.12f).toInt(); val y1 = (py - h * 0.03f).toInt().coerceAtLeast(y0 + 1)
        var n = 0; var sx = 0L; var sy = 0L
        for (y in y0 until y1) for (x in 0 until w) {
            if (abs(cur[y * w + x] - old[y * w + x]) > Config.DIFF_THRESHOLD) { n++; sx += x; sy += y }
        }
        if (n < 3) return
        val cx = sx.toFloat() / n; val cy = sy.toFloat() / n
        val now = System.nanoTime()
        val dt = (now - lastT) / 1e6f
        if (lastT != 0L && dt in 1f..200f) {
            vx = 0.5f * vx + 0.5f * (cx - lastCx) / dt
            vy = 0.5f * vy + 0.5f * (cy - lastCy) / dt
        } else { vx = 0f; vy = 0f }
        lastCx = cx; lastCy = cy; lastT = now

        var tx = cx
        if (vy > 0.005f) {                       // ball heading down: predict landing x with wall bounces
            val t = (py - cy) / vy
            val ww = w.toFloat()
            var x = cx + vx * t
            x = ((x % (2 * ww)) + 2 * ww) % (2 * ww)
            if (x > ww) x = 2 * ww - x
            tx = x
        }
        BotAccessibilityService.instance?.moveTo(tx * Config.SCALE, Config.paddleY * sh)
    }

    override fun onDestroy() {
        vd?.release(); reader?.close(); proj?.stop(); thread.quitSafely()
        BotAccessibilityService.instance?.release()
        super.onDestroy()
    }
}
