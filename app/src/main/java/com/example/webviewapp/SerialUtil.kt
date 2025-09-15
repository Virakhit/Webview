package com.example.webviewapp

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader

object SerialUtil {
    private const val TAG = "SerialUtil"

    // Try multiple strategies to obtain a hardware serial / SN
    fun getDeviceSerial(ctx: Context): String {
        Log.d(TAG, "=== Serial Number Detection ===")

        val finalSerial = getDeviceSerialInternal(ctx)
        Log.d(TAG, "Final Selected Serial: $finalSerial")
        return finalSerial
    }
    
    private fun getDeviceSerialInternal(ctx: Context): String {
        // 0. Priority method: vendor/system property that may contain SN (sys.product.sn often used)
        try {
            val propSerial = getSystemProperty("sys.product.sn")
            Log.d(TAG, "sys.product.sn: $propSerial")
            if (!propSerial.isNullOrBlank()) return propSerial
        } catch (e: Exception) {
            Log.e(TAG, "sys.product.sn check failed: ${e.message}")
        }

        // 1. Fallback to getprop method
        try {
            val adbLike = getSerialFromGetProp()
            Log.d(TAG, "getprop-derived serial: $adbLike")
            if (!adbLike.isNullOrBlank()) return adbLike
        } catch (e: Exception) {
            Log.e(TAG, "getprop check failed: ${e.message}")
        }

        // 2. Common system properties used by POS vendors (sunmi/landis)
        try {
            val props = listOf("ro.serialno", "ro.boot.serialno", "persist.sys.serialno", "ro.product.serial", "ro.gsm.imei")
            for (p in props) {
                val v = getSystemProperty(p)
                Log.d(TAG, "property $p: $v")
                if (!v.isNullOrBlank()) return v
            }
        } catch (e: Exception) {
            Log.e(TAG, "System properties check failed: ${e.message}")
        }

        // 3. Some devices expose serial in /sys/class/misc or /proc
        try {
            val candidates = listOf(
                "/sys/class/android_usb/android0/iSerial",
                "/sys/class/misc/sunxi_serial/serial",
                "/sys/class/socinfo/serial",
                "/sys/devices/platform/soc.0/serial",
                "/proc/cpuinfo"
            )
            for (c in candidates) {
                val v = readFirstLine(File(c))
                Log.d(TAG, "file $c: $v")
                if (!v.isNullOrBlank()) {
                    // If reading /proc/cpuinfo, search for serial line
                    if (c.endsWith("cpuinfo", ignoreCase = true)) {
                        val sn = parseCpuInfoSerial(File(c))
                        if (!sn.isNullOrBlank()) return sn
                    } else {
                        return v
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "File system check failed: ${e.message}")
        }

        // 4. Fallback to ANDROID_ID
        val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
    Log.d(TAG, "Falling back to ANDROID_ID: $androidId")
        return androidId ?: "unknown"
    }
    
    // Function to get serial number using specific method for testing
    fun getSerialByMethod(ctx: Context, method: String): String? {
        Log.d(TAG, "Testing method: $method")
        
        return when (method.lowercase()) {
            "sys_product_sn" -> getSystemProperty("sys.product.sn")
            "build_serial" -> {
                try {
                    @Suppress("DEPRECATION")
                    Build.SERIAL
                } catch (e: Exception) {
                    Log.e(TAG, "Build.SERIAL error: ${e.message}")
                    null
                }
            }
            "build_getserial" -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Build.getSerial()
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Build.getSerial() error: ${e.message}")
                    null
                }
            }
            "ro_serialno" -> getSystemProperty("ro.serialno")
            "ro_boot_serialno" -> getSystemProperty("ro.boot.serialno")
            "persist_sys_serialno" -> getSystemProperty("persist.sys.serialno")
            "ro_product_serial" -> getSystemProperty("ro.product.serial")
            "ro_gsm_imei" -> getSystemProperty("ro.gsm.imei")
            "getprop" -> getSerialFromGetProp()
            "android_id" -> Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
            "cpuinfo" -> parseCpuInfoSerial(File("/proc/cpuinfo"))
            "sys_serial" -> readFirstLine(File("/sys/class/android_usb/android0/iSerial"))
            else -> {
                Log.w(TAG, "Unknown method: $method")
                null
            }
        }
    }
    
    // Function to test all methods and return results map
    fun testAllMethods(ctx: Context): Map<String, String?> {
        Log.d(TAG, "=== Testing All Serial Number Methods ===")
        
        val methods = listOf(
            "sys_product_sn", "build_serial", "build_getserial", "ro_serialno", "ro_boot_serialno",
            "persist_sys_serialno", "ro_product_serial", "ro_gsm_imei", 
            "getprop", "android_id", "cpuinfo", "sys_serial"
        )
        
        val results = mutableMapOf<String, String?>()
        
        for (method in methods) {
            val result = getSerialByMethod(ctx, method)
            results[method] = result
            Log.d(TAG, "Method [$method]: $result")
        }
        
        Log.d(TAG, "=== Test Complete ===")
        return results
    }

    // intentionally omitted Build.getSerial() / Build.SERIAL to avoid permission/deprecation issues

    private fun getSystemProperty(key: String): String? {
        return try {
            val clz = Class.forName("android.os.SystemProperties")
            val get = clz.getMethod("get", String::class.java)
            val v = get.invoke(null, key) as? String
            if (!v.isNullOrBlank()) v else null
        } catch (e: Exception) {
            null
        }
    }

    // Attempt to execute `getprop <key>` for common serial keys, or run `getprop` and parse the output.
    private fun getSerialFromGetProp(): String? {
        val preferredKeys = listOf("ro.serialno", "ro.boot.serialno", "persist.sys.serialno", "ro.product.serial")

        // Try direct getprop <key> first for preferred keys
        for (k in preferredKeys) {
            val v = execGetProp(k)
            if (!v.isNullOrBlank()) return v.trim()
        }

        // Parse full getprop output into key/value map and prefer preferredKeys
        val all = execGetProp(null) ?: return null
        val map = mutableMapOf<String, String>()
        all.lines().forEach { line ->
            // line examples: [ro.serialno]: [247ACD201002]
            val m = Regex("""^\[(.+?)\]: \[(.*)\]$""").find(line)
            if (m != null && m.groupValues.size >= 3) {
                val key = m.groupValues[1].trim()
                val value = m.groupValues[2].trim()
                map[key] = value
            }
        }

        for (k in preferredKeys) {
            val v = map[k]
            if (!v.isNullOrBlank()) {
                Log.d(TAG, "getprop: chosen key=$k value=$v")
                return v
            }
        }

        // fallback: prefer alphanumeric serial-like values (6-32 chars) that contain letters
        for ((key, v) in map) {
            if (!v.isNullOrBlank() && v.matches(Regex("^[0-9A-Za-z]{6,32}$")) && v.matches(Regex(".*[A-Za-z].*"))) {
                Log.d(TAG, "getprop: fallback chosen key=$key value=$v")
                return v
            }
        }

        // fallback: numeric-only long values (>8 chars) but ignore cache_key numeric prefixes
        for ((key, v) in map) {
            if (!v.isNullOrBlank() && v.matches(Regex("^[0-9]{9,}") ) && !key.startsWith("cache_key")) {
                Log.d(TAG, "getprop: numeric fallback key=$key value=$v")
                return v
            }
        }

        return null
    }

    private fun execGetProp(key: String?): String? {
        return try {
            // Use `sh -c` wrapper which is more reliable in restricted environments
            val candidates = if (key.isNullOrBlank()) listOf(arrayOf("sh", "-c", "getprop"), arrayOf("sh", "-c", "/system/bin/getprop"))
            else listOf(arrayOf("sh", "-c", "getprop $key"), arrayOf("sh", "-c", "/system/bin/getprop $key"))
            var result: String? = null
            for (cmd in candidates) {
                try {
                    val pb = ProcessBuilder(*cmd)
                    pb.redirectErrorStream(true)
                    val proc = pb.start()
                    val out = StringBuilder()
                    InputStreamReader(proc.inputStream).use { isr ->
                        BufferedReader(isr).use { br ->
                            var line: String? = br.readLine()
                            while (line != null) {
                                out.append(line).append('\n')
                                line = br.readLine()
                            }
                        }
                    }
                    proc.waitFor()
                    val s = out.toString().trim()
                    if (s.isNotEmpty()) {
                        result = s
                        Log.d(TAG, "getprop(${cmd.joinToString(" ")}): ${s.replace("\n", " | ")}")
                        break
                    }
                } catch (e: Exception) {
                    // try next candidate
                }
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun readFirstLine(file: File): String? {
        return try {
            if (!file.exists() || !file.canRead()) return null
            BufferedReader(FileReader(file)).use { it.readLine()?.trim() }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseCpuInfoSerial(file: File): String? {
        return try {
            if (!file.exists() || !file.canRead()) return null
            file.bufferedReader().useLines { lines ->
                for (l in lines) {
                    if (l.startsWith("Serial") || l.startsWith("serial", ignoreCase = true)) {
                        val parts = l.split(":")
                        if (parts.size >= 2) return parts[1].trim()
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
