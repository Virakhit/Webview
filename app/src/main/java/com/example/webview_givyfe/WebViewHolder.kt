package com.example.webview_givyfe

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object WebViewHolder {
    private const val TAG = "WebViewHolder"
    private const val TARGET_URL = "https://cyberforall.net/DEV/GivyFE/register"

    private var _webView: WebView? = null
    val isLoaded: Boolean
        get() = _webView != null

    fun getWebView(): WebView? = _webView

    // Preload a WebView on background (call from Activity.onCreate of splash)
    fun preload(context: Context) {
        if (_webView != null) return
        try {
            val wv = WebView(context.applicationContext)
            applyCommonSettings(wv)
            wv.loadUrl(TARGET_URL)
            _webView = wv
            Log.d(TAG, "preload: started")
        } catch (e: Exception) {
            Log.e(TAG, "preload: failed", e)
        }
    }

    fun stealWebView(): WebView? {
        val tmp = _webView
        _webView = null
        return tmp
    }

    private fun applyCommonSettings(webView: WebView) {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadsImagesAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.mediaPlaybackRequiresUserGesture = false
        settings.userAgentString = settings.userAgentString + " WebViewGivyFE/1.0"

        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }
    }
}
