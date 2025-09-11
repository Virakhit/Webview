package com.example.webviewapp

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.webkit.PermissionRequest
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import android.app.Activity
import android.os.Bundle
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
                // If a file chooser is pending and camera permission was requested for it,
                // open the camera picker. Otherwise, grant any pending web permission request.
                if (pendingFileChooser != null) {
                    openCameraPicker()
                } else {
                    onCameraPermissionGranted()
                }
            } else {
                // Permission denied: you can show a message or degrade functionality
            }
        }

    // Keep a pending PermissionRequest from the WebView while we ask the user for app permission
    private var pendingPermissionRequest: PermissionRequest? = null
    
    // Pending file chooser callback from WebView
    private var pendingFileChooser: ValueCallback<Array<Uri>>? = null

    // Launcher for picking files
    private val pickFilesLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        val data = result.data
        val resultUris: Array<Uri>? = when {
            result.resultCode != Activity.RESULT_OK -> null
            data == null -> {
                // Possibly returned from camera intent with EXTRA_OUTPUT -> use cameraPhotoUri
                cameraPhotoUri?.let { arrayOf(it) }
            }
            data.clipData != null -> {
                val count = data.clipData!!.itemCount
                Array(count) { i -> data.clipData!!.getItemAt(i).uri }
            }
            data.data != null -> arrayOf(data.data!!)
            else -> null
        }

        pendingFileChooser?.onReceiveValue(resultUris)
        pendingFileChooser = null
        // Clear the temporary camera uri after delivering the result
        cameraPhotoUri = null
    }

    // Helper to hold a camera photo uri while launching camera
    private var cameraPhotoUri: Uri? = null

    // Launcher to request read external storage permission
    private val requestStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // If there was a pending file chooser, reopen it by sending an intent
                pendingFileChooser?.let { _ ->
                    openFilePicker()
                }
            } else {
                pendingFileChooser?.onReceiveValue(null)
                pendingFileChooser = null
            }
        }


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

    private fun openFilePicker(params: FileChooserParams? = null) {
        val intent: Intent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        try {
            pickFilesLauncher.launch(Intent.createChooser(intent, "Select file"))
        } catch (e: Exception) {
            // If intent fails, return null to callback
            pendingFileChooser?.onReceiveValue(null)
            pendingFileChooser = null
        }
    }

    private fun openCameraPicker() {
        val takePictureIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        // Ensure there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            var photoFile: File? = null
            try {
                photoFile = createImageFile()
            } catch (ex: IOException) {
                // Error occurred while creating the File
            }
            // Continue only if the File was successfully created
            photoFile?.also {
                val authority = "${applicationContext.packageName}.fileprovider"
                val photoURI: Uri = FileProvider.getUriForFile(this, authority, it)
                cameraPhotoUri = photoURI
                takePictureIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoURI)
                // Grant temporary permission to camera activity
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    pickFilesLauncher.launch(takePictureIntent)
                } catch (e: Exception) {
                    pendingFileChooser?.onReceiveValue(null)
                    pendingFileChooser = null
                }
            } ?: run {
                pendingFileChooser?.onReceiveValue(null)
                pendingFileChooser = null
            }
        } else {
            pendingFileChooser?.onReceiveValue(null)
            pendingFileChooser = null
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File = getExternalFilesDir(null)!!
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        )
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

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // Save callback and launch picker (request storage permission first if needed)
                pendingFileChooser = filePathCallback
                val readPerm = android.Manifest.permission.READ_EXTERNAL_STORAGE
                // Determine if capture is requested by the web input.
                val wantsCapture = fileChooserParams?.isCaptureEnabled == true ||
                        (fileChooserParams?.acceptTypes?.contains("image/*") == true && fileChooserParams.isCaptureEnabled)

                if (wantsCapture) {
                    // Ensure camera permission
                    val cameraPerm = android.Manifest.permission.CAMERA
                    if (ContextCompat.checkSelfPermission(this@MainActivity, cameraPerm) == PackageManager.PERMISSION_GRANTED) {
                        openCameraPicker()
                    } else {
                        // request camera permission; once granted, onCameraPermissionGranted will call openCameraPicker if pendingFileChooser exists
                        pendingPermissionRequest = null
                        requestCameraPermissionLauncher.launch(cameraPerm)
                    }
                    return true
                }

                if (ContextCompat.checkSelfPermission(this@MainActivity, readPerm) == PackageManager.PERMISSION_GRANTED) {
                    openFilePicker(fileChooserParams)
                } else {
                    requestStoragePermissionLauncher.launch(readPerm)
                }
                return true
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
