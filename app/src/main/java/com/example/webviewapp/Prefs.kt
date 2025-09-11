package com.example.webviewapp

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "app_prefs"
    private const val KEY_COMPANY = "companyId"
    private const val KEY_BRAND = "brandId"
    private const val KEY_OUTLET = "outletId"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun save(ctx: Context, company: String, brand: String, outlet: String) {
        prefs(ctx).edit()
            .putString(KEY_COMPANY, company)
            .putString(KEY_BRAND, brand)
            .putString(KEY_OUTLET, outlet)
            .apply()
    }

    fun get(ctx: Context): Triple<String?, String?, String?> {
        val p = prefs(ctx)
        return Triple(p.getString(KEY_COMPANY, null), p.getString(KEY_BRAND, null), p.getString(KEY_OUTLET, null))
    }

    fun isConfigured(ctx: Context): Boolean {
        val (c, b, o) = get(ctx)
        return !c.isNullOrBlank() && !b.isNullOrBlank() && !o.isNullOrBlank()
    }
}
