package com.example.webview_givyfe

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.activity.ComponentActivity

class SplashActivity : ComponentActivity() {
    // splash duration in ms: keep for ~3 seconds while preloading WebView
    private val SPLASH_MS = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Start a preloading WebView in background
        WebViewHolder.preload(applicationContext)

        // Start simple animation (defined in layout as android:animation)
        val logo = findViewById<ImageView>(R.id.splash_logo)
        logo?.let {
            // short fade + subtle pulse that finishes within SPLASH_MS
            it.alpha = 0f
            it.animate().alpha(1f).setDuration(350).start()
            it.animate().scaleX(1.05f).scaleY(1.05f).setDuration(350).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(350).start()
            }.start()
        }

        // Delay then launch MainActivity
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            // finish splash so back doesn't return
            finish()
        }, SPLASH_MS)
    }
}
