package com.example.webview_givyfe

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object WebViewHolder {
    private const val TAG = "WebViewHolder"

    // เก็บ URL เป้าหมายแบบคงที่เพื่อใช้ในการ preload และให้สอดคล้องกับ MainActivity
    // (ถ้าต้องการเปลี่ยน URL ให้เปลี่ยนที่นี่และใน MainActivity ให้ตรงกัน)
    private const val TARGET_URL = "https://cyberforall.net/DEV/GivyFE/register"
    // ตัวอย่าง URL สำรองที่เคยใช้ในการพัฒนา
    // private const val TARGET_URL = "https://cyberforall.net/DEV/GivyFE/"
    // private const val TARGET_URL = "http://192.168.2.108:4200/"

    // ตัวแปรภายในเก็บ WebView ที่ preload ไว้ (อาจเป็น null ถ้ายังไม่ได้ preload)
    private var _webView: WebView? = null
    // ตรวจสอบว่า WebView ถูก preload เรียบร้อยหรือยัง
    val isLoaded: Boolean
        get() = _webView != null

    // คืนค่า WebView ที่เก็บไว้ (อาจเป็น null)
    fun getWebView(): WebView? = _webView

    // preload: สร้าง WebView ใน background (ควรเรียกจาก SplashActivity.onCreate)
    // จุดประสงค์: โหลดหน้าเว็บเบื้องต้นเพื่อลดเวลาแสดงผลเมื่อเปลี่ยนไป MainActivity
    fun preload(context: Context) {
        if (_webView != null) return
        try {
            val wv = WebView(context.applicationContext)
            applyCommonSettings(wv)
            // เริ่มโหลด URL เป้าหมายทันที
            wv.loadUrl(TARGET_URL)
            _webView = wv
            Log.d(TAG, "preload: started")
        } catch (e: Exception) {
            Log.e(TAG, "preload: failed", e)
        }
    }

    // stealWebView: คืน WebView ที่ preload ไว้ให้กับ caller และเคลียร์ internal reference
    // เพื่อให้ caller เป็นเจ้าของ WebView ต่อไป (MainActivity จะนำไปใช้และจัดการ lifecycle)
    fun stealWebView(): WebView? {
        val tmp = _webView
        _webView = null
        return tmp
    }

    // applyCommonSettings: ตั้งค่า WebSettings และ Cookie ให้ WebView ที่สร้างขึ้น
    // ค่าที่กำหนดรวมถึงการเปิด JavaScript, DOM storage, การจัดการ mixed content และ user-agent
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

        // เปิดการรับคุกกี้และอนุญาต third-party cookies ถ้า API รองรับ
        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }
    }
}
