package com.example.webview_givyfe

import android.content.Context
import android.webkit.JavascriptInterface
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
}