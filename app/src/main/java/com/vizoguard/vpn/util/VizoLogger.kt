package com.vizoguard.vpn.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object VizoLogger {
    private const val MAX_FILE_SIZE = 2 * 1024 * 1024L // 2 MB
    private const val MAX_FILES = 3
    private var logDir: File? = null
    private var isDebug = true
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var sessionId = UUID.randomUUID().toString().take(8)

    fun init(context: Context, debug: Boolean = true) {
        logDir = File(context.filesDir, "logs").also { it.mkdirs() }
        isDebug = debug
        sessionId = UUID.randomUUID().toString().take(8)
        i("SYSTEM", "Session started: $sessionId")
    }

    fun d(tag: String, message: String) = log("D", tag, message)
    fun i(tag: String, message: String) = log("I", tag, message)
    fun w(tag: String, message: String) = log("W", tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message: ${throwable.message}" else message
        log("E", tag, msg)
    }

    // Convenience methods for VPN state transitions
    fun vpnState(from: String, to: String) = i("VPN", "$from → $to")
    fun apiCall(endpoint: String, success: Boolean, statusCode: Int? = null) {
        val status = if (success) "OK" else "FAIL${statusCode?.let { " ($it)" } ?: ""}"
        i("API", "$endpoint → $status")
    }
    fun licenseEvent(event: String) = i("LICENSE", event)
    fun systemEvent(event: String) = i("SYSTEM", event)

    private fun log(level: String, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val line = "[$timestamp] [$sessionId] [$level] [$tag] $message"

        // Logcat (debug builds only)
        if (isDebug) {
            when (level) {
                "D" -> Log.d(tag, message)
                "I" -> Log.i(tag, message)
                "W" -> Log.w(tag, message)
                "E" -> Log.e(tag, message)
            }
        }

        // File (always)
        writeToFile(line)
    }

    @Synchronized
    private fun writeToFile(line: String) {
        val dir = logDir ?: return
        val file = File(dir, "vizoguard.log")

        // Rotate if too large
        if (file.exists() && file.length() > MAX_FILE_SIZE) {
            rotate(dir)
        }

        try {
            FileWriter(file, true).use { writer ->
                writer.appendLine(line)
            }
        } catch (e: Exception) {
            // Can't log a logging failure — just drop it
        }
    }

    private fun rotate(dir: File) {
        // Delete oldest, shift others
        for (i in MAX_FILES - 1 downTo 1) {
            val older = File(dir, "vizoguard.$i.log")
            val newer = if (i == 1) File(dir, "vizoguard.log") else File(dir, "vizoguard.${i - 1}.log")
            if (newer.exists()) {
                older.delete()
                newer.renameTo(older)
            }
        }
    }

    fun getLogFile(context: Context): File? {
        val dir = logDir ?: File(context.filesDir, "logs")
        val file = File(dir, "vizoguard.log")
        return if (file.exists()) file else null
    }

    fun getAllLogText(context: Context): String {
        val dir = logDir ?: File(context.filesDir, "logs")
        val files = dir.listFiles()?.filter { it.name.startsWith("vizoguard") }?.sortedBy { it.name } ?: return ""
        return files.joinToString("\n---\n") { it.readText() }
    }

    fun clearLogs() {
        logDir?.listFiles()?.forEach { it.delete() }
    }
}
