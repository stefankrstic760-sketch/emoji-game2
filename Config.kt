package com.example.pongbot

object Config {
    /** Paddle height as a fraction of screen height (0 = top, 1 = bottom). Tune with the slider. */
    @Volatile var paddleY = 0.85f
    /** Brightness change needed to count a pixel as "moved". */
    const val DIFF_THRESHOLD = 40
    /** Frames are analysed at 1/SCALE size for speed. */
    const val SCALE = 4
}
