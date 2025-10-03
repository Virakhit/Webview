package com.example.webview_givyfe.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [CompanyInfo::class],
    version = 1,
    exportSchema = false
)
// AppDatabase: กำหนดฐานข้อมูล ของแอป
// - entities: ระบุ entity ที่ใช้ (CompanyInfo)
// - version: เวอร์ชันของ schema (ใช้เมื่อต้อง migration)
// คลาสนี้สืบทอดจาก RoomDatabase และประกาศ DAO ที่ใช้งาน
abstract class AppDatabase : RoomDatabase() {
    
    // คืนค่า DAO สำหรับจัดการ CompanyInfo (insert, query, update, delete ฯลฯ)
    abstract fun companyInfoDao(): CompanyInfoDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        // getDatabase: คืน singleton instance ของ AppDatabase
        // - ใช้ synchronized เพื่อให้แน่ใจว่ามีการสร้าง instance เดียวกันในหลาย thread
        // - ใช้ context.applicationContext เพื่อป้องกัน memory leak จาก Activity context
        // - Room.databaseBuilder สร้าง/เปิดฐานข้อมูลชื่อ "givy_database"
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "givy_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}