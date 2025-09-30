package com.example.webview_givyfe

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.example.webview_givyfe.data.AppDatabase
import com.example.webview_givyfe.data.CompanyInfo
import org.json.JSONObject

class WebAppInterface(private val context: Context, private val callback: CompanyInfoCallback) {
    
    interface CompanyInfoCallback {
        fun onCompanyInfoLoaded(companyId: String?, brandId: String?, outletId: String?)
    }
    
    private val database = AppDatabase.getDatabase(context)
    private val dao = database.companyInfoDao()
    
    init {
        // โหลดข้อมูลเมื่อเริ่มต้น
        loadCompanyInfo()
    }
    
    @JavascriptInterface
    fun saveCompanyInfo(companyId: String?, brandId: String?, outletId: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            val companyInfo = CompanyInfo(
                id = 1,
                companyId = companyId,
                brandId = brandId,
                outletId = outletId
            )
            dao.insertOrUpdateCompanyInfo(companyInfo)
            
            // ส่งข้อมูลกลับไปยัง callback
            CoroutineScope(Dispatchers.Main).launch {
                callback.onCompanyInfoLoaded(companyId, brandId, outletId)
            }
        }
    }
    
    @JavascriptInterface
    fun getCompanyInfo(): String {
        return try {
            val companyInfo = runBlocking {
                dao.getCompanyInfo()
            }
            
            val json = JSONObject()
            json.put("companyId", companyInfo?.companyId ?: "")
            json.put("brandId", companyInfo?.brandId ?: "")
            json.put("outletId", companyInfo?.outletId ?: "")
            json.toString()
        } catch (e: Exception) {
            "{\"companyId\":\"\",\"brandId\":\"\",\"outletId\":\"\"}"
        }
    }
    
    @JavascriptInterface
    fun hasCompanyInfo(): Boolean {
        return try {
            runBlocking {
                dao.hasCompleteCompanyInfo()
            }
        } catch (e: Exception) {
            false
        }
    }
    
    @JavascriptInterface
    fun clearCompanyInfo() {
        CoroutineScope(Dispatchers.IO).launch {
            dao.clearCompanyInfo()
            
            // ส่งข้อมูลว่างกลับไปยัง callback
            CoroutineScope(Dispatchers.Main).launch {
                callback.onCompanyInfoLoaded(null, null, null)
            }
        }
    }
    
    private fun loadCompanyInfo() {
        CoroutineScope(Dispatchers.IO).launch {
            val companyInfo = dao.getCompanyInfo()
            
            CoroutineScope(Dispatchers.Main).launch {
                callback.onCompanyInfoLoaded(
                    companyInfo?.companyId,
                    companyInfo?.brandId,
                    companyInfo?.outletId
                )
            }
        }
    }

    @JavascriptInterface
    fun getDeviceSerial(): String {
        fun isInvalidSerial(s: String?): Boolean {
            val v = s?.trim()?.lowercase(Locale.US) ?: return true
            if (v.isEmpty()) return true
            if (v == "null" || v == "unknown") return true
            if (v.matches(Regex("^0+$"))) return true
            return false
        }

        try {
            val spClass = Class.forName("android.os.SystemProperties")
            val getMethod = spClass.getMethod("get", String::class.java, String::class.java)
            val sn = try {
                getMethod.invoke(null, "sys.product.sn", "") as? String
            } catch (e: Exception) {
                null
            }
            if (!isInvalidSerial(sn)) {
                Log.d("WebAppInterface", "getDeviceSerial: using sys.product.sn='$sn'")
                return sn!!.trim()
            }
        } catch (ignored: Exception) {
        }

        try {
            val buildClass = Build::class.java
            try {
                val getSerialMethod = buildClass.getMethod("getSerial")
                val serialObj = try {
                    getSerialMethod.invoke(null)
                } catch (e: Exception) {
                    null
                }
                val sn = (serialObj as? String)
                if (!isInvalidSerial(sn)) {
                    Log.d("WebAppInterface", "getDeviceSerial: using Build.getSerial()='$sn'")
                    return sn!!.trim()
                }
            } catch (e: NoSuchMethodException) {
            }
        } catch (ignored: Exception) { }

        val fallback = try { Build.MODEL ?: "" } catch (e: Exception) { "" }
        val final = fallback.ifEmpty { "unknown" }
        Log.d("WebAppInterface", "getDeviceSerial: falling back to '$final'")
        return final
    }
}