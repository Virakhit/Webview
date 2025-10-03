package com.example.webview_givyfe

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.webview_givyfe.data.AppDatabase
import com.example.webview_givyfe.data.CompanyInfo
import org.json.JSONObject

class WebAppInterface(private val context: Context, private val callback: CompanyInfoCallback) {
    
    interface CompanyInfoCallback {
        fun onCompanyInfoLoaded(companyId: String?, brandId: String?, outletId: String?)
        fun onSerialNumberLoaded(serialNumber: String?)
    }
    
    private val database = AppDatabase.getDatabase(context)
    private val dao = database.companyInfoDao()
    
    // เก็บข้อมูล company ไว้ใน cache เพื่อลดการเรียกฐานข้อมูลแบบบล็อก
    @Volatile
    private var cachedCompanyInfo: CompanyInfo? = null
    
    // เก็บ serial number ไว้ใน cache เพื่อลดการอ่านค่าแบบบล็อก
    @Volatile
    private var cachedSerialNumber: String? = null
    
    init {
        // โหลดข้อมูลเริ่มต้น: company info และ serial number ลงใน cache
        loadCompanyInfo()
        loadSerialNumber()
    }
    
    @JavascriptInterface
    fun saveCompanyInfo(companyId: String?, brandId: String?, outletId: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val companyInfo = CompanyInfo(
                    id = 1,
                    companyId = companyId,
                    brandId = brandId,
                    outletId = outletId
                )
                dao.insertOrUpdateCompanyInfo(companyInfo)
                
                // อัปเดต cache ด้วยข้อมูลที่บันทึก
                cachedCompanyInfo = companyInfo

                // แจ้ง callback ทาง UI thread ว่าข้อมูลถูกบันทึกเรียบร้อย
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onCompanyInfoLoaded(companyId, brandId, outletId)
                }
            } catch (e: Exception) {
                Log.e("WebAppInterface", "Error saving company info", e)
            }
        }
    }
    
    @JavascriptInterface
    fun getCompanyInfo(): String {
        return try {
            // คืนค่า JSON โดยใช้ข้อมูลจาก cache (ถ้าไม่มีจะคืนค่าว่าง)
            // โดยปกติหน้า JavaScript ควรใช้ callback ที่ WebAppInterface โทรกลับมา
            val json = JSONObject()
            json.put("companyId", cachedCompanyInfo?.companyId ?: "")
            json.put("brandId", cachedCompanyInfo?.brandId ?: "")
            json.put("outletId", cachedCompanyInfo?.outletId ?: "")
            json.toString()
        } catch (e: Exception) {
            Log.e("WebAppInterface", "Error getting company info", e)
            "{\"companyId\":\"\",\"brandId\":\"\",\"outletId\":\"\"}"
        }
    }
    
    @JavascriptInterface
    fun hasCompanyInfo(): Boolean {
        return try {
            // ตรวจสอบค่าใน cache แทนการเรียกฐานข้อมูลแบบ synchronous
            cachedCompanyInfo?.let { info ->
                !info.companyId.isNullOrBlank() && 
                !info.brandId.isNullOrBlank() && 
                !info.outletId.isNullOrBlank()
            } ?: false
        } catch (e: Exception) {
            Log.e("WebAppInterface", "Error checking company info", e)
            false
        }
    }
    
    @JavascriptInterface
    fun clearCompanyInfo() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                dao.clearCompanyInfo()
                
                // เคลียร์ cache ของ company info
                cachedCompanyInfo = null

                // แจ้ง callback ทาง UI thread ว่าข้อมูลถูกลบ (ส่งค่าว่าง)
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onCompanyInfoLoaded(null, null, null)
                }
            } catch (e: Exception) {
                Log.e("WebAppInterface", "Error clearing company info", e)
            }
        }
    }
    
    @JavascriptInterface
    fun getDeviceSerial(): String {
        return try {
            // คืนค่า serial จาก cache ถ้ามี มิฉะนั้นอ่านจากระบบและเก็บลง cache
            cachedSerialNumber ?: getDeviceSerialFromSystem().also { 
                cachedSerialNumber = it 
            }
        } catch (e: Exception) {
            Log.e("WebAppInterface", "Error getting device serial", e)
            "unknown"
        }
    }
    
    @JavascriptInterface
    fun saveSerialNumber(serialNumber: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // บันทึก serial ลงใน SharedPreferences
                val prefs = context.getSharedPreferences("givy_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("serial_number", serialNumber).apply()
                
                // อัปเดต cache
                cachedSerialNumber = serialNumber

                // แจ้ง callback ทาง UI thread ว่า serial ถูกบันทึก
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSerialNumberLoaded(serialNumber)
                }
            } catch (e: Exception) {
                Log.e("WebAppInterface", "Error saving serial number", e)
            }
        }
    }
    
    @JavascriptInterface
    fun getSavedSerial(): String {
        return try {
            // คืนค่า serial จาก cache หรือค่าว่างถ้ายังไม่มี
            cachedSerialNumber ?: ""
        } catch (e: Exception) {
            Log.e("WebAppInterface", "Error getting saved serial", e)
            ""
        }
    }
    
    private fun loadCompanyInfo() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val companyInfo = dao.getCompanyInfo()
                
                // อัปเดต cache และแจ้ง callback ทาง UI thread ว่าดึงข้อมูลจากฐานข้อมูลเสร็จ
                cachedCompanyInfo = companyInfo

                CoroutineScope(Dispatchers.Main).launch {
                    callback.onCompanyInfoLoaded(
                        companyInfo?.companyId,
                        companyInfo?.brandId,
                        companyInfo?.outletId
                    )
                }
            } catch (e: Exception) {
                Log.e("WebAppInterface", "Error loading company info", e)
            }
        }
    }
    
    private fun loadSerialNumber() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // พยายามโหลด serial จาก SharedPreferences ก่อน
                val prefs = context.getSharedPreferences("givy_prefs", Context.MODE_PRIVATE)
                val savedSerial = prefs.getString("serial_number", null)

                // ถ้าไม่มี serial ที่บันทึกไว้ ให้ดึงจากระบบเป็น fallback
                val finalSerial = if (savedSerial.isNullOrBlank()) {
                    getDeviceSerialFromSystem()
                } else {
                    savedSerial
                }

                // อัปเดต cache และแจ้ง callback ทาง UI thread
                cachedSerialNumber = finalSerial

                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSerialNumberLoaded(finalSerial)
                }
            } catch (e: Exception) {
                Log.e("WebAppInterface", "Error loading serial number", e)
            }
        }
    }
    
    private fun getDeviceSerialFromSystem(): String {
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