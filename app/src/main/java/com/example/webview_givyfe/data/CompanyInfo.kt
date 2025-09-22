package com.example.webview_givyfe.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "company_info")
data class CompanyInfo(
    @PrimaryKey
    var id: Int = 1, // เก็บเป็น record เดียว
    var companyId: String? = null,
    var brandId: String? = null,
    var outletId: String? = null
)