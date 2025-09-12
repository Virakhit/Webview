package com.example.webviewapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.util.Log
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraCaptureActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var captureButton: AppCompatImageButton
    private var imageCapture: ImageCapture? = null
    private var photoFile: File? = null
    private var isCapturing: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_camera_capture)
            previewView = findViewById(R.id.previewView)
            captureButton = findViewById(R.id.captureButton)

            startCamera()

            captureButton.setOnClickListener {
                if (!isCapturing) {
                    takePhoto()
                }
            }
        } catch (t: Throwable) {
            // Log and fail gracefully to avoid a hard crash; request logcat from user for root cause
            Log.e("CameraCapture", "onCreate failed", t)
            try {
                Toast.makeText(this, "Camera failed to start", Toast.LENGTH_SHORT).show()
            } catch (ignored: Exception) {
            }
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e("CameraCapture", "startCamera failed", e)
                setResult(RESULT_CANCELED)
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        try {
            photoFile = createImageFile()
        } catch (e: IOException) {
            Log.e("CameraCapture", "createImageFile failed", e)
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile!!).build()
        // mark capturing state and disable further taps
        isCapturing = true
        captureButton.isEnabled = false

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val authority = "${applicationContext.packageName}.fileprovider"
                    val uri: Uri = FileProvider.getUriForFile(this@CameraCaptureActivity, authority, photoFile!!)
                    val result = Intent()
                    result.data = uri
                    // Provide ClipData for receivers that expect multiple items and grant read permission
                    result.clipData = android.content.ClipData.newUri(contentResolver, "Image", uri)
                    result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    // Also grant to any resolvers (safe-guard)
                    try {
                        grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {
                        // ignore
                    }
                    setResult(RESULT_OK, result)
                } catch (e: Exception) {
                    Log.e("CameraCapture", "onImageSaved: returning URI failed", e)
                    setResult(RESULT_CANCELED)
                } finally {
            // reset capturing state before finishing
            isCapturing = false
            captureButton.isEnabled = true
            finish()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraCapture", "Image capture failed", exception)
                setResult(RESULT_CANCELED)
                // reset capturing state on error so user can retry
                isCapturing = false
                captureButton.isEnabled = true
                finish()
            }
        })
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File = getExternalFilesDir(null)!!
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }
}
