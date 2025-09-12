package com.example.webviewapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object DeviceRegistrar {
    /**
     * Posts device identifier to API. Expected JSON response:
     * { "registered": true, "company": "..", "brand": "..", "outlet": ".." }
     * On success will save company/brand/outlet into Prefs.
     * Callback is invoked on main thread with `registered` boolean.
     *
     * NOTE: Adjust apiUrl to your server endpoint. This implementation uses simple HttpURLConnection
     * and assumes a 200 response with JSON body.
     */
    fun registerDevice(ctx: Context, apiUrl: String, callback: (registered: Boolean) -> Unit) {
        Thread {
            val deviceId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
                ?: java.util.UUID.randomUUID().toString()

            try {
                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")

                val payload = JSONObject().put("deviceId", deviceId).toString()
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                if (code == 200) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(resp)
                    val registered = json.optBoolean("registered", false)
                    if (registered) {
                        val company = json.optString("company", "")
                        val brand = json.optString("brand", "")
                        val outlet = json.optString("outlet", "")
                        if (company.isNotEmpty() && brand.isNotEmpty() && outlet.isNotEmpty()) {
                            Prefs.save(ctx, company, brand, outlet)
                        }
                    }
                    Handler(Looper.getMainLooper()).post { callback(registered) }
                } else {
                    Handler(Looper.getMainLooper()).post { callback(false) }
                }
            } catch (e: Exception) {
                // network or parse error
                Handler(Looper.getMainLooper()).post { callback(false) }
            }
        }.start()
    }
}
