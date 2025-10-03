package com.example.webview_givyfe

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

// MainActivity: จัดการ WebView, การขออนุญาตกล้อง, การเลือกไฟล์ (camera/file chooser), อ่าน serial
// ของอุปกรณ์
// และส่งข้อมูล (serial, company info) กลับไปยังหน้าเว็บผ่าน JavaScript interface
class MainActivity : ComponentActivity(), WebAppInterface.CompanyInfoCallback {

    // Callback สำหรับรับผลการเลือกไฟล์จาก WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    // Uri ของรูปที่ถ่ายจากกล้องชั่วคราว
    private var cameraImageUri: Uri? = null
    private lateinit var webView: WebView
    private lateinit var webAppInterface: WebAppInterface

    // Activity Result API launchers
    private lateinit var cameraActivityLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    // มุมมอง overlay ที่แสดงเป็น splash ขณะโหลดหน้าเว็บ
    private var splashOverlayView: View? = null

    // ข้อมูลบริษัท/สาขา/อุปกรณ์ที่อาจส่งไปยังเว็บ
    private var companyId: String? = null
    private var brandId: String? = null
    private var outletId: String? = null
    private var deviceSerial: String? = null

    companion object {
        // ค่าคงที่ที่ใช้ใน Activity
        private const val FILE_CHOOSER_REQUEST_CODE = 1001
        private const val CAMERA_PERMISSION_REQUEST_CODE = 2001

        // URL เป้าหมายสำหรับ WebView
        private const val TARGET_URL = "https://cyberforall.net/DEV/GivyFE/register"
        // private const val TARGET_URL = "https://cyberforall.net/DEV/GivyFE"
        // private const val TARGET_URL = "http://192.168.2.108:4200/"
    }

    // onCreate: ลงทะเบียน permission launcher, activity launcher, เตรียม WebAppInterface,
    // กำหนด layout และพยายามนำ WebView ที่ preload ไว้กลับมาใช้ใหม่ (ลดเวลาโหลด)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ลงทะเบียน permission launcher (Activity Result API)
        permissionLauncher =
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    // ไม่ต้องการ logic พิเศษ ตรงนี้เก็บไว้เพื่อให้เรียก permission ได้
                }
        requestCameraPermissionIfNeeded()

        // ลงทะเบียน launcher สำหรับเรียก activity (กล้อง) และรับผล
        cameraActivityLauncher =
                registerForActivityResult(StartActivityForResult()) { result ->
                    val callback = filePathCallback
                    filePathCallback = null
                    if (callback == null) return@registerForActivityResult

                    // ถ้าผลไม่ OK ให้คืนค่า null
                    if (result.resultCode != Activity.RESULT_OK) {
                        callback.onReceiveValue(null)
                        return@registerForActivityResult
                    }

                    // แปลงผลที่ได้เป็น Array<Uri> (รองรับ single, clipData, หรือ cameraImageUri)
                    val data = result.data
                    val results: Array<Uri>? =
                            when {
                                (data == null || data.data == null) && cameraImageUri != null ->
                                        arrayOf(cameraImageUri!!)
                                data?.clipData != null -> {
                                    val clip = data.clipData!!
                                    Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                                }
                                data?.data != null -> arrayOf(data.data!!)
                                else -> null
                            }

                    // ให้สิทธิ์อ่านไฟล์กับแพ็กเกจที่จำเป็น (เพื่อให้ camera activity
                    // ส่งผลกลับมาได้)
                    if (results != null) {
                        for (uri in results) {
                            try {
                                grantUriPermission(
                                        packageName,
                                        uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (ignored: Exception) {}
                        }
                    }

                    // ส่งผลกลับไปยัง WebView callback
                    callback.onReceiveValue(results)
                    cameraImageUri = null
                }

        // สร้าง/เตรียม JavaScript interface ที่ใช้สื่อสารกับหน้าเว็บ
        webAppInterface = WebAppInterface(this, this)

        setContentView(R.layout.activity_main)

        // หา container สำหรับวาง WebView และมองหา splash overlay (ถ้ามี)
        val container = findViewById<FrameLayout>(R.id.webview_container)
        splashOverlayView =
                try {
                    findViewById<View>(R.id.splash_overlay)
                } catch (e: Exception) {
                    null
                }

        // พยายามใช้ WebView ที่ preload ไว้จาก SplashActivity เพื่อลดเวลารอ
        val pre = WebViewHolder.stealWebView()
        if (pre != null) {
            webView = pre
            setupWebView(webView)

            // ถ้า WebView ยังมี parent ให้ตัดมันออกก่อนใส่ใน container ใหม่
            try {
                val parent = webView.parent
                if (parent is ViewGroup) parent.removeView(webView)
            } catch (ignored: Exception) {}
            container.addView(
                    webView,
                    ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            )

            // ถ้าเป็นครั้งแรกให้โหลด URL เป้าหมาย หาก WebView ว่าง
            if (savedInstanceState == null) {
                if (webView.url.isNullOrEmpty()) {
                    webView.loadUrl(TARGET_URL)
                }
            } else {
                webView.restoreState(savedInstanceState)
            }
            try {
                if (splashOverlayView != null && webView.progress >= 100) {
                    splashOverlayView?.post { splashOverlayView?.visibility = View.GONE }
                }
            } catch (ignored: Exception) {}
        } else {
            // กรณีไม่มี WebView preload ให้สร้างใหม่และเพิ่มเข้า container
            webView = WebView(this)
            setupWebView(webView)
            container.addView(
                    webView,
                    ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            )
            if (savedInstanceState == null) {
                webView.loadUrl(TARGET_URL)
            } else {
                webView.restoreState(savedInstanceState)
            }
            try {
                if (splashOverlayView != null && webView.progress >= 100) {
                    splashOverlayView?.post { splashOverlayView?.visibility = View.GONE }
                }
            } catch (ignored: Exception) {}
        }
    }

    // เก็บสถานะของ WebView เมื่อ activity ถูกบีบอัด (configuration change ฯลฯ)
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) {
            webView.saveState(outState)
        }
    }

    // ตรวจสอบและขออนุญาตกล้องถ้ายังไม่ได้รับ
    private fun requestCameraPermissionIfNeeded() {
        val hasCamera =
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
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
        // กำหนด WebSettings ที่จำเป็นสำหรับการทำงานของเว็บแอป
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadsImagesAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.mediaPlaybackRequiresUserGesture = false
        settings.userAgentString = settings.userAgentString + " WebViewGivyFE/1.0"

        // เปิด remote debugging เฉพาะใน build ที่ debuggable เพื่อช่วย debug หน้าเว็บ
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val isDebuggable =
                    try {
                        (applicationInfo.flags and
                                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    } catch (e: Exception) {
                        false
                    }
            if (isDebuggable) {
                WebView.setWebContentsDebuggingEnabled(true)
                Log.d("MainActivity", "WebView debugging enabled (debug build)")
            }
        }

        Log.d("MainActivity", "setupWebView: initializing WebView settings")

        // ผูก JavaScript interface เพื่อให้หน้าเว็บเรียก native functions ได้
        webView.addJavascriptInterface(webAppInterface, "AndroidInterface")

        // พยายามส่ง serial ให้หน้าเว็บตอนต้น เมื่อ interface พร้อมแล้ว
        try {
            webView.post {
                val serialEarly = getDeviceSerial()
                sendSerialToWeb(serialEarly)
            }
        } catch (ignored: Exception) {}

        // ตั้งค่าคุกกี้ให้รับได้ และอนุญาต third-party cookies ถ้า API รองรับ
        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }

        // กำหนด WebViewClient เพื่อควบคุมการโหลดหน้า, จัดการข้อผิดพลาด และเหตุการณ์ต่าง ๆ
        webView.webViewClient =
                object : WebViewClient() {
                    // ไม่ override URL loading ให้ WebView เป็นคนจัดการทั้งหมด
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        Log.d("MainActivity", "shouldOverrideUrlLoading: url=$url")
                        return false
                    }

                    // จด log เมื่อลองเริ่มโหลดหน้า
                    override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: android.graphics.Bitmap?
                    ) {
                        super.onPageStarted(view, url, favicon)
                        Log.d("MainActivity", "onPageStarted: url=$url")
                    }

                    // เมื่อลงท้ายการโหลดหน้า: ส่ง serial และ company info ให้หน้าเว็บ และซ่อน
                    // splash
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d("MainActivity", "onPageFinished: url=$url title=${'$'}{view?.title}")
                        val serial = getDeviceSerial()
                        sendSerialToWeb(serial)

                        sendCompanyInfoToWeb()

                        try {
                            splashOverlayView?.post { splashOverlayView?.visibility = View.GONE }
                        } catch (ignored: Exception) {}
                    }

                    // เฉพาะการดีบัก: ยอมรับข้อผิดพลาด SSL เมื่อรันดีบักบิลด์เพื่อให้สามารถทดสอบได้
                    // คำเตือน: การดำเนินการกับข้อผิดพลาด SSL นี้ไม่ปลอดภัย ห้ามเปิดใช้งานในการใช้งานจริง

                    // override fun onReceivedSslError(
                    //         view: WebView?,
                    //         handler: android.webkit.SslErrorHandler?,
                    //         error: android.net.http.SslError?
                    // ) {
                    //     Log.e(
                    //             "MainActivity",
                    //             "onReceivedSslError: primaryError=${'$'}{error?.primaryError} url=${'$'}{error?.url}"
                    //     )
                    //     try {
                    //         val isDebuggable =
                    //                 try {
                    //                     (this@MainActivity.applicationInfo.flags and
                    //                             android.content.pm.ApplicationInfo
                    //                                     .FLAG_DEBUGGABLE) != 0
                    //                 } catch (e: Exception) {
                    //                     false
                    //                 }
                    //         if (isDebuggable) {
                    //             handler?.proceed()
                    //             Log.w(
                    //                     "MainActivity",
                    //                     "onReceivedSslError: proceeding because app is debuggable"
                    //             )
                    //         } else {
                    //             handler?.cancel()
                    //             Log.w(
                    //                     "MainActivity",
                    //                     "onReceivedSslError: cancelled (not debuggable)"
                    //             )
                    //         }
                    //     } catch (e: Exception) {
                    //         Log.e(
                    //                 "MainActivity",
                    //                 "onReceivedSslError: exception while handling ssl error",
                    //                 e
                    //         )
                    //         handler?.cancel()
                    //     }
                    // }

                    // บันทึกข้อผิดพลาดทั่วไปเพื่อการวิเคราะห์
                    override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        Log.e(
                                "MainActivity",
                                "onReceivedError: url=${'$'}{request?.url} - code=${'$'}{error?.errorCode} - desc=${'$'}{error?.description}"
                        )
                    }

                    // บันทึก HTTP error ที่เกิดขึ้นขณะโหลด resource
                    override fun onReceivedHttpError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            errorResponse: android.webkit.WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        Log.e(
                                "MainActivity",
                                "onReceivedHttpError: url=${'$'}{request?.url} - status=${'$'}{errorResponse?.statusCode}"
                        )
                    }
                }

        // WebChromeClient: จัดการ permission ของ WebRTC และ file chooser (camera)
        webView.webChromeClient =
                object : WebChromeClient() {
                    // เมื่อหน้าเว็บขอ permission เช่น video capture ให้ตรวจสอบและอนุญาตเฉพาะเมื่อมี
                    // permission ของแอป
                    override fun onPermissionRequest(request: PermissionRequest) {
                        val resources = request.resources
                        val allow = resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                        if (allow &&
                                        ContextCompat.checkSelfPermission(
                                                this@MainActivity,
                                                Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                        } else {
                            request.deny()
                        }
                    }

                    // จัดการ <input type="file"> ที่ต้องการถ่ายรูป: สร้างไฟล์ภาพชั่วคราว ให้สิทธิ์
                    // แล้วเรียกกล้อง
                    override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                    ): Boolean {
                        this@MainActivity.filePathCallback?.onReceiveValue(null)
                        this@MainActivity.filePathCallback = filePathCallback

                        // สร้าง intent สำหรับถ่ายภาพ
                        var takePictureIntent: Intent? = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        if (takePictureIntent != null) {
                            val imageFile = tryCreateImageFile()
                            if (imageFile != null) {
                                cameraImageUri =
                                        FileProvider.getUriForFile(
                                                this@MainActivity,
                                                applicationContext.packageName + ".fileprovider",
                                                imageFile
                                        )
                                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                                takePictureIntent.addFlags(
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )

                                // มอบสิทธิ์ URI ให้กับ activity ที่จะจัดการ intent (กล้อง)
                                val resInfoList =
                                        packageManager.queryIntentActivities(
                                                takePictureIntent,
                                                PackageManager.MATCH_DEFAULT_ONLY
                                        )
                                for (resolveInfo in resInfoList) {
                                    val packageName = resolveInfo.activityInfo.packageName
                                    grantUriPermission(
                                            packageName,
                                            cameraImageUri,
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )
                                }

                                return try {
                                    // เรียกกล้องผ่าน Activity Result API
                                    cameraActivityLauncher.launch(takePictureIntent)
                                    true
                                } catch (e: ActivityNotFoundException) {
                                    this@MainActivity.filePathCallback = null
                                    cameraImageUri = null
                                    false
                                }
                            }
                        }

                        // หากไม่สามารถใช้กล้องได้ ให้คืนค่า failure
                        this@MainActivity.filePathCallback = null
                        cameraImageUri = null
                        return false
                    }
                }
    }

    // สร้างไฟล์ชั่วคราวสำหรับเก็บภาพที่ถ่าย (ใช้ externalCache หรือ cache ของแอป)
    private fun tryCreateImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val storageDir = externalCacheDir ?: cacheDir
            File.createTempFile("JPEG_${'$'}timeStamp_", ".jpg", storageDir)
        } catch (e: Exception) {
            null
        }
    }

    // ใช้ OnBackPressedDispatcher เพื่อจัดการปุ่ม Back: ถ้า WebView ย้อนหน้าได้ให้ย้อน
    // ถ้าไม่ให้ระบบจัดการปกติ
    override fun onStart() {
        super.onStart()
        onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        if (::webView.isInitialized && webView.canGoBack()) {
                            webView.goBack()
                        } else {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
        )
    }

    // อ่าน serial อุปกรณ์ โดยพยายามใช้หลาย fallback (SystemProperties -> Build.getSerial ->
    // Build.MODEL)
    private fun getDeviceSerial(): String {
        fun isInvalidSerial(s: String?): Boolean {
            val v = s?.trim()?.lowercase(Locale.US) ?: return true
            if (v.isEmpty()) return true
            if (v == "null" || v == "unknown") return true
            if (v.matches(Regex("^0+$"))) return true
            return false
        }

        // พยายามอ่าน sys.product.sn ผ่าน SystemProperties (reflection)
        try {
            val spClass = Class.forName("android.os.SystemProperties")
            val getMethod = spClass.getMethod("get", String::class.java, String::class.java)
            val sn =
                    try {
                        getMethod.invoke(null, "sys.product.sn", "") as? String
                    } catch (e: Exception) {
                        null
                    }
            if (!isInvalidSerial(sn)) {
                Log.d("MainActivity", "getDeviceSerial: using sys.product.sn='$sn'")
                return sn!!.trim()
            }
        } catch (ignored: Exception) {}

        // ลองใช้ Build.getSerial() ผ่าน reflection (บาง platform อาจไม่มี)
        try {
            val buildClass = Build::class.java
            try {
                val getSerialMethod = buildClass.getMethod("getSerial")
                val serialObj =
                        try {
                            getSerialMethod.invoke(null)
                        } catch (e: Exception) {
                            null
                        }
                val sn = (serialObj as? String)
                if (!isInvalidSerial(sn)) {
                    Log.d("MainActivity", "getDeviceSerial: using Build.getSerial()='$sn'")
                    return sn!!.trim()
                }
            } catch (e: NoSuchMethodException) {}
        } catch (ignored: Exception) {}

        // ถ้าไม่ได้ serial จริง ให้ fallback เป็น model ของอุปกรณ์ หรือ "unknown"
        val fallback =
                try {
                    Build.MODEL ?: ""
                } catch (e: Exception) {
                    ""
                }
        val final = fallback.ifEmpty { "unknown" }
        Log.d("MainActivity", "getDeviceSerial: falling back to '$final'")
        return final
    }

    // ส่ง serial ไปยังหน้าเว็บผ่าน evaluateJavascript (escape string ให้ปลอดภัย)
    private fun sendSerialToWeb(serial: String) {
        if (!::webView.isInitialized) return
        val cleaned =
                when (serial.trim().lowercase(Locale.US)) {
                    "", "null", "unknown" -> ""
                    else -> serial.trim()
                }
        Log.d("MainActivity", "sendSerialToWeb: sending serial='$cleaned'")
        val quoted = org.json.JSONObject.quote(cleaned)
        val js = "window.onNativeSerial && window.onNativeSerial($quoted);"
        webView.post { webView.evaluateJavascript(js, null) }
    }

    // ทำความสะอาด WebView เมื่อ activity ถูกทำลายเพื่อลด memory leak
    override fun onDestroy() {
        super.onDestroy()

        if (::webView.isInitialized) {
            try {
                val parent = webView.parent
                if (parent is ViewGroup) {
                    parent.removeView(webView)
                }

                webView.clearHistory()
                webView.clearCache(true)
                webView.loadUrl("about:blank")
                webView.onPause()
                try {
                    webView.pauseTimers()
                } catch (ignored: Exception) {}
                webView.removeAllViews()
                try {
                    webView.setWebChromeClient(android.webkit.WebChromeClient())
                } catch (ignored: Exception) {}
                try {
                    webView.setWebViewClient(android.webkit.WebViewClient())
                } catch (ignored: Exception) {}
                try {
                    webView.clearFocus()
                } catch (ignored: Exception) {}
                webView.destroy()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error cleaning up WebView", e)
            }
        }
    }

    // Callback จาก WebAppInterface เมื่อโหลดข้อมูล company info เสร็จ
    override fun onCompanyInfoLoaded(companyId: String?, brandId: String?, outletId: String?) {
        this.companyId = companyId
        this.brandId = brandId
        this.outletId = outletId

        // ส่งข้อมูลไปยังหน้าเว็บ (Angular) หากพร้อม
        sendCompanyInfoToWeb()
    }

    // Callback เมื่อโหลด serial number เสร็จจาก WebAppInterface
    override fun onSerialNumberLoaded(serialNumber: String?) {
        this.deviceSerial = serialNumber

        // ส่ง serial ที่ได้ไปยังหน้าเว็บ
        sendSerialToWeb(serialNumber ?: "")
    }

    // แปลง company/brand/outlet เป็น JSON แล้วส่งไปยังหน้าเว็บผ่าน evaluateJavascript
    private fun sendCompanyInfoToWeb() {
        if (!::webView.isInitialized) return

        val json = JSONObject()
        json.put("companyId", companyId ?: "")
        json.put("brandId", brandId ?: "")
        json.put("outletId", outletId ?: "")

        val quotedJson = JSONObject.quote(json.toString())
        val js = "window.onNativeCompanyInfo && window.onNativeCompanyInfo($quotedJson);"
        webView.post { webView.evaluateJavascript(js, null) }
    }
}
