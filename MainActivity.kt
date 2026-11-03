package com.example.pongbot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private val REQ = 42

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pad = 40
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }.also { col.addView(it) }

        col.addView(TextView(this).apply {
            text = "1) Enable \"Emoji Pong Bot\" under Accessibility.\n" +
                   "2) Tap Start and allow screen capture.\n" +
                   "3) Open Instagram, tap an emoji to start the game.\n" +
                   "4) Set the slider to the paddle's height on screen."
        })
        btn("1. Open Accessibility settings") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        btn("2. Start bot") {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ)
        }
        btn("Stop bot") { stopService(Intent(this, CaptureService::class.java)); BotAccessibilityService.instance?.release() }

        val label = TextView(this).apply { text = "Paddle height: ${(Config.paddleY * 100).toInt()}%" }
        col.addView(label)
        col.addView(SeekBar(this).apply {
            max = 100; progress = (Config.paddleY * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    Config.paddleY = p / 100f; label.text = "Paddle height: $p%"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        setContentView(col)

        if (android.os.Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ && res == RESULT_OK && data != null) {
            startForegroundService(Intent(this, CaptureService::class.java)
                .putExtra("code", res).putExtra("data", data))
        }
    }
}
