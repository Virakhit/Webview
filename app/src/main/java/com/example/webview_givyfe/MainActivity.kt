package com.example.webview_givyfe

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), WebAppInterface.CompanyInfoCallback {

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null
    private lateinit var webView: WebView
    private lateinit var webAppInterface: WebAppInterface

    // ตัวแปรสำหรับเก็บ CompanyId, BrandId, OutletId
    private var companyId: String? = null
    private var brandId: String? = null
    private var outletId: String? = null

    companion object {
        private const val FILE_CHOOSER_REQUEST_CODE = 1001
        private const val CAMERA_PERMISSION_REQUEST_CODE = 2001
        // private const val TARGET_URL = "https://cyberforall.net/DEV/GivyFE/"
        private const val TARGET_URL = "http://172.20.10.3:4200/register"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ask for camera permission at first launch
        requestCameraPermissionIfNeeded()

        // Initialize WebAppInterface
        webAppInterface = WebAppInterface(this, this)

        // Try to reuse a preloaded WebView stolen from SplashActivity
        val pre = WebViewHolder.stealWebView()
        if (pre != null) {
            webView = pre
            // attach to this activity
            setContentView(webView)
            // ensure settings and interfaces are present
            setupWebView(webView)
            if (savedInstanceState == null) {
                // if preloaded already started loading, do not reload; otherwise load
                if (!webView.url.isNullOrEmpty()) {
                    // already loading
                } else {
                    webView.loadUrl(TARGET_URL)
                }
            } else {
                webView.restoreState(savedInstanceState)
            }
        } else {
            // Build a WebView programmatically
            webView = WebView(this)
            setContentView(webView)
            setupWebView(webView)
            if (savedInstanceState == null) {
                webView.loadUrl(TARGET_URL)
            } else {
                webView.restoreState(savedInstanceState)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) {
            webView.saveState(outState)
        }
    }

    private fun requestCameraPermissionIfNeeded() {
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasCamera) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadsImagesAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.mediaPlaybackRequiresUserGesture = false
        settings.userAgentString = settings.userAgentString + " WebViewGivyFE/1.0"
        
        // Enable remote debugging for WebView so chrome://inspect can show titles/URLs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        
        Log.d("MainActivity", "setupWebView: initializing WebView settings")

        // เพิ่ม JavaScript Interface
        webView.addJavascriptInterface(webAppInterface, "AndroidInterface")

        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                Log.d("MainActivity", "shouldOverrideUrlLoading: url=$url")
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("MainActivity", "onPageStarted: url=$url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("MainActivity", "onPageFinished: url=$url title=${'$'}{view?.title}")
                // Push device serial number to the page when it finishes loading
                val serial = getDeviceSerial()
                sendSerialToWeb(serial)

                // ส่งข้อมูล CompanyId, BrandId, OutletId ไปยัง Angular หากมี
                sendCompanyInfoToWeb()
            }

            // Debug-only: accept SSL errors when running a debug build to allow testing
            // WARNING: Proceeding on SSL errors is insecure. Do NOT enable in production.
            override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                Log.e("MainActivity", "onReceivedSslError: primaryError=${'$'}{error?.primaryError} url=${'$'}{error?.url}")
                try {
                    // Only auto-proceed in debug builds
                    val isDebuggable = try {
                        (this@MainActivity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    } catch (e: Exception) {
                        false
                    }
                    if (isDebuggable) {
                        handler?.proceed()
                        Log.w("MainActivity", "onReceivedSslError: proceeding because app is debuggable")
                    } else {
                        handler?.cancel()
                        Log.w("MainActivity", "onReceivedSslError: cancelled (not debuggable)")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "onReceivedSslError: exception while handling ssl error", e)
                    handler?.cancel()
                }
            }

            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                super.onReceivedError(view, request, error)
                Log.e("MainActivity", "onReceivedError: url=${'$'}{request?.url} - code=${'$'}{error?.errorCode} - desc=${'$'}{error?.description}")
            }

            override fun onReceivedHttpError(view: WebView?, request: android.webkit.WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.e("MainActivity", "onReceivedHttpError: url=${'$'}{request?.url} - status=${'$'}{errorResponse?.statusCode}")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Handle camera/mic permission from WebRTC/WebView
            override fun onPermissionRequest(request: PermissionRequest) {
                // Grant only if it requests video capture and we have camera permission
                val resources = request.resources
                val allow = resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                if (allow && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                } else {
                    request.deny()
                }
            }

            // Handle <input type="file" accept="image/*">
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                // Camera-only mode: create and launch camera intent directly
                var takePictureIntent: Intent? = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (takePictureIntent != null) {
                    val imageFile = tryCreateImageFile()
                    if (imageFile != null) {
                        cameraImageUri = FileProvider.getUriForFile(
                            this@MainActivity,
                            applicationContext.packageName + ".fileprovider",
                            imageFile
                        )
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                        takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

                        // Grant URI permissions to camera activities that will handle the intent
                        val resInfoList = packageManager.queryIntentActivities(takePictureIntent, PackageManager.MATCH_DEFAULT_ONLY)
                        for (resolveInfo in resInfoList) {
                            val packageName = resolveInfo.activityInfo.packageName
                            grantUriPermission(packageName, cameraImageUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        return try {
                            startActivityForResult(takePictureIntent, FILE_CHOOSER_REQUEST_CODE)
                            true
                        } catch (e: ActivityNotFoundException) {
                            this@MainActivity.filePathCallback = null
                            cameraImageUri = null
                            false
                        }
                    }
                }

                // If camera not available or failed to create file, signal failure
                this@MainActivity.filePathCallback = null
                cameraImageUri = null
                return false
            }
        }
    }

    private fun tryCreateImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val storageDir = externalCacheDir ?: cacheDir
            File.createTempFile("JPEG_${'$'}timeStamp_", ".jpg", storageDir)
        } catch (e: Exception) {
            null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            val callback = filePathCallback
            filePathCallback = null
            if (callback == null) return

            if (resultCode != Activity.RESULT_OK) {
                callback.onReceiveValue(null)
                return
            }

            val results: Array<Uri>? = when {
                // No data but we have a camera image URI
                (data == null || data.data == null) && cameraImageUri != null -> arrayOf(cameraImageUri!!)

                // Multiple items (e.g. when using a file manager)
                data?.clipData != null -> {
                    val clip = data.clipData!!
                    Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                }

                // Single content URI returned
                data?.data != null -> arrayOf(data.data!!)

                else -> null
            }

            // If we returned a cameraImageUri, make sure WebView has permission to access it
            if (results != null) {
                for (uri in results) {
                    try {
                        grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (ignored: Exception) { }
                }
            }

            callback.onReceiveValue(results)
            cameraImageUri = null
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            // No UI change needed here; WebView will be able to use camera when granted
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // Read device serial from system properties with fallbacks
    private fun getDeviceSerial(): String {
        // Try reading sys.product.sn via SystemProperties (reyflection), then fallbacks
        try {
            val spClass = Class.forName("android.os.SystemProperties")
            val getMethod = spClass.getMethod("get", String::class.java, String::class.java)
            val sn = getMethod.invoke(null, "sys.product.sn", "") as String
            if (!sn.isNullOrEmpty()) return sn
        } catch (ignored: Exception) {
        }

        // Additional common fallbacks
        try {
            // Use reflection to call Build.getSerial() when available to avoid
            // NoSuchMethodError on some devices/platforms.
            val buildClass = Build::class.java
            try {
                val getSerialMethod = buildClass.getMethod("getSerial")
                val serialObj = getSerialMethod.invoke(null)
                val sn = (serialObj as? String) ?: ""
                if (sn.isNotEmpty()) return sn
            } catch (e: NoSuchMethodException) {
                // Method not available, continue to other fallbacks
            }
        } catch (ignored: Exception) { }

        // Last resort: hardware and build fields
    val fallback = (Build.SERIAL ?: "")
        return fallback.ifEmpty { "unknown" }
    }

    // Send the serial to the page via evaluateJavascript (escapes string)
    private fun sendSerialToWeb(serial: String) {
        if (!::webView.isInitialized) return
    val quoted = org.json.JSONObject.quote(serial)
        val js = "window.onNativeSerial && window.onNativeSerial($quoted);"
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    // Callback เมื่อข้อมูล CompanyInfo ถูกโหลดจาก SQLite
    override fun onCompanyInfoLoaded(companyId: String?, brandId: String?, outletId: String?) {
        this.companyId = companyId
        this.brandId = brandId
        this.outletId = outletId
        
        // ส่งข้อมูลไปยัง Angular
        sendCompanyInfoToWeb()
    }

    // ส่งข้อมูล CompanyInfo ไปยัง Angular
    private fun sendCompanyInfoToWeb() {
        if (!::webView.isInitialized) return
        
        val json = JSONObject()
        json.put("companyId", companyId ?: "")
        json.put("brandId", brandId ?: "")
        json.put("outletId", outletId ?: "")
        
        val quotedJson = JSONObject.quote(json.toString())
        val js = "window.onNativeCompanyInfo && window.onNativeCompanyInfo($quotedJson);"
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }
}