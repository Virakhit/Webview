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
        // 0. Prefer adb-like serial by reading getprop or specific properties via shell if available
        // try {
        //     val adbLike = getSerialFromGetProp()
        //     Log.d(TAG, "getSerialFromGetProp => $adbLike")
        //     if (!adbLike.isNullOrBlank()) return adbLike
        // } catch (e: Exception) {
        //     // ignore
        // }

    // 1. Skip Build.SERIAL/getSerial due to platform restrictions; prefer getprop/system properties

        // 2. Common system properties used by POS vendors (sunmi/landis)
        try {
            val props = listOf("ro.serialno", "ro.boot.serialno", "persist.sys.serialno", "ro.product.serial", "ro.gsm.imei")
            for (p in props) {
                val v = getSystemProperty(p)
                if (!v.isNullOrBlank()) return v
            }
        } catch (e: Exception) {
            // ignore
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
            // ignore
        }

        // 4. Fallback to ANDROID_ID
        val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        Log.d(TAG, "Falling back to ANDROID_ID: $androidId")
        return androidId ?: "unknown"
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
