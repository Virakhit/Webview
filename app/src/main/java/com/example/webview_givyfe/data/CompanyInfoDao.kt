package com.example.webview_givyfe.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CompanyInfoDao {
    
    @Query("SELECT * FROM company_info WHERE id = 1 LIMIT 1")
    suspend fun getCompanyInfo(): CompanyInfo?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCompanyInfo(companyInfo: CompanyInfo)
    
    @Query("DELETE FROM company_info")
    suspend fun clearCompanyInfo()
    
    @Query("SELECT COUNT(*) > 0 FROM company_info WHERE companyId IS NOT NULL AND brandId IS NOT NULL AND outletId IS NOT NULL")
    suspend fun hasCompleteCompanyInfo(): Boolean
}