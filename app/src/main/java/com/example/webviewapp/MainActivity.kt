package com.example.webviewapp

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.webkit.PermissionRequest
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    
    // Activity Result launcher for requesting camera permission
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                onCameraPermissionGranted()
            } else {
                // Permission denied: you can show a message or degrade functionality
            }
        }

    // Keep a pending PermissionRequest from the WebView while we ask the user for app permission
    private var pendingPermissionRequest: PermissionRequest? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    // Request camera permission at runtime if needed
    checkAndRequestCameraPermission()

        if (!Prefs.isConfigured(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)
        setupWebView()

    val (company, brand, outlet) = Prefs.get(this)
    val url = "https://cyberforall.net/GivyFE/$company,$brand,$outlet"
        webView.loadUrl(url)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun checkAndRequestCameraPermission() {
        val permission = android.Manifest.permission.CAMERA
        when (ContextCompat.checkSelfPermission(this, permission)) {
            PackageManager.PERMISSION_GRANTED -> onCameraPermissionGranted()
            else -> requestCameraPermissionLauncher.launch(permission)
        }
    }

    private fun onCameraPermissionGranted() {
        // Camera permission granted. If a web page previously requested camera access,
        // grant the pending PermissionRequest now so the web page receives the allow event.
        pendingPermissionRequest?.let { req ->
            try {
                req.grant(req.resources)
            } catch (e: Exception) {
                // ignore
            } finally {
                pendingPermissionRequest = null
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                // Called when a web page requests permission (e.g., camera via getUserMedia)
                runOnUiThread {
                    val cameraPerm = android.Manifest.permission.CAMERA
                    if (ContextCompat.checkSelfPermission(this@MainActivity, cameraPerm) == PackageManager.PERMISSION_GRANTED) {
                        // App already has camera permission -> grant the web request
                        try {
                            request.grant(request.resources)
                        } catch (e: Exception) {
                            // ignore
                        }
                    } else {
                        // Save pending request and ask the user for app-level camera permission
                        pendingPermissionRequest = request
                        requestCameraPermissionLauncher.launch(cameraPerm)
                    }
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                if (request != null && request == pendingPermissionRequest) {
                    pendingPermissionRequest = null
                }
                super.onPermissionRequestCanceled(request)
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                findViewById<View>(R.id.progressBar)?.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                findViewById<View>(R.id.progressBar)?.visibility = View.GONE
            }
        }
    }
}
