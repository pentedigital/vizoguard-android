package com.vizoguard.vpn.util

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

private val IS_DEBUG_BUILD: Boolean by lazy {
    try {
        val clazz = Class.forName("com.vizoguard.vpn.BuildConfig")
        clazz.getField("DEBUG").getBoolean(null)
    } catch (_: Exception) {
        false // assume release if BuildConfig unavailable (R8 stripped it)
    }
}

object Tag {
    const val SYSTEM = "SYSTEM"
    const val SERVICE = "SERVICE"
    const val VPN = "VPN"
    const val API = "API"
    const val LICENSE = "LICENSE"
}

object VizoLogger {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    private const val MAX_FILE_SIZE = 2 * 1024 * 1024L // 2 MB
    private const val MAX_FILES = 3
    private const val MAX_STACK_LINES = 15
    private const val MAX_LOG_READ_CHARS = 512 * 1024 // 512 K chars for getAllLogText
    private var logDir: File? = null
    private var isDebug = IS_DEBUG_BUILD
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.of("UTC"))
    private var sessionId = UUID.randomUUID().toString().take(8)
    @Volatile private var initialized = false
    private val fileLock = ReentrantLock()
    private var writer: BufferedWriter? = null
    private var writerFile: File? = null
    private var linesSinceFlush = 0
    private const val FLUSH_INTERVAL = 10

    @Synchronized
    fun init(context: Context, debug: Boolean = IS_DEBUG_BUILD) {
        if (initialized) return
        initialized = true
        logDir = File(context.filesDir, "logs").also { it.mkdirs() }
        isDebug = debug
        sessionId = UUID.randomUUID().toString().take(8)
        i(Tag.SYSTEM, "Session started: $sessionId")
    }

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message)
    fun w(tag: String, message: String) = log(Level.WARN, tag, message)
    fun w(tag: String, message: String, throwable: Throwable?) = log(Level.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.ERROR, tag, message, throwable)
    }

    // Convenience methods for VPN state transitions
    fun vpnState(from: String, to: String) = i(Tag.VPN, "$from → $to")
    fun apiCall(endpoint: String, success: Boolean, statusCode: Int? = null) {
        val status = if (success) "OK" else "FAIL${statusCode?.let { " ($it)" } ?: ""}"
        i(Tag.API, "$endpoint → $status")
    }
    fun licenseEvent(event: String) = i(Tag.LICENSE, event)
    fun systemEvent(event: String) = i(Tag.SYSTEM, event)

    private fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        // Skip DEBUG logs in release builds
        if (level == Level.DEBUG && !isDebug) return

        // Build the log line outside the lock — no contention for string work
        val levelChar = when (level) {
            Level.DEBUG -> "D"
            Level.INFO -> "I"
            Level.WARN -> "W"
            Level.ERROR -> "E"
        }
        val timestamp = dateFormat.format(Instant.now())
        val traceSnippet = throwable?.stackTraceToString()
            ?.lines()?.take(MAX_STACK_LINES)?.joinToString("\n")
        val fullMessage = if (traceSnippet != null) "$message:\n$traceSnippet" else message
        val line = "[$timestamp] [$sessionId] [$levelChar] [$tag] $fullMessage"

        // Logcat (debug builds only)
        if (isDebug) {
            when (level) {
                Level.DEBUG -> Log.d(tag, message)
                Level.INFO -> Log.i(tag, message)
                Level.WARN -> Log.w(tag, message)
                Level.ERROR -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
            }
        }

        // File write is the only part that needs synchronization — use tryLock with timeout
        // to avoid blocking the caller indefinitely if another thread holds the lock
        if (fileLock.tryLock(500, TimeUnit.MILLISECONDS)) {
            try {
                writeToFile(line)
            } finally {
                fileLock.unlock()
            }
        } else {
            // Lock contention — drop this log line rather than block the caller
            if (isDebug) Log.w("VizoLogger", "Dropped log line due to lock contention: ${line.take(80)}")
        }
    }

    /** Redact sensitive data (license keys, ss:// URLs) before writing to file */
    private fun sanitize(msg: String): String {
        return msg
            .replace(Regex("VIZO-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}"), "VIZO-****-****-****-****")
            .replace(Regex("ss://[^\\s]+"), "ss://[REDACTED]")
            .replace(Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"), "[IP_REDACTED]")
    }

    /** Must only be called under [fileLock] */
    private fun writeToFile(line: String) {
        val dir = logDir ?: return
        val file = File(dir, "vizoguard.log")

        // Rotate if too large — close writer first
        if (file.exists() && file.length() > MAX_FILE_SIZE) {
            closeWriter()
            rotate(dir)
        }

        try {
            // Reuse buffered writer — only open if needed or file changed
            if (writer == null || writerFile != file) {
                closeWriter()
                writer = BufferedWriter(FileWriter(file, true))
                writerFile = file
            }
            writer!!.appendLine(sanitize(line))
            linesSinceFlush++
            if (linesSinceFlush >= FLUSH_INTERVAL) {
                writer!!.flush()
                linesSinceFlush = 0
            }
        } catch (_: Exception) {
            closeWriter()
        }
    }

    private fun closeWriter() {
        try { writer?.close() } catch (_: Exception) {}
        writer = null
        writerFile = null
        linesSinceFlush = 0
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
        // Flush writer and snapshot file list under lock to avoid torn reads
        if (fileLock.tryLock(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            try { writer?.flush() } finally { fileLock.unlock() }
        }
        val dir = logDir ?: File(context.filesDir, "logs")
        val files = dir.listFiles()?.filter { it.name.startsWith("vizoguard") }?.sortedBy { it.name }
            ?: return ""
        val sb = StringBuilder()
        for (file in files) {
            if (sb.length >= MAX_LOG_READ_CHARS) {
                sb.append("\n--- truncated (${MAX_LOG_READ_CHARS / 1024} KB limit) ---")
                break
            }
            if (sb.isNotEmpty()) sb.append("\n---\n")
            val remaining = MAX_LOG_READ_CHARS - sb.length
            val text = file.readText()
            if (text.length <= remaining) {
                sb.append(text)
            } else {
                sb.append(text, 0, remaining)
                sb.append("\n--- truncated (${MAX_LOG_READ_CHARS / 1024} KB limit) ---")
                break
            }
        }
        return sb.toString()
    }

    fun clearLogs() {
        fileLock.lock()
        try {
            closeWriter()
            logDir?.listFiles()?.forEach { it.delete() }
        } finally {
            fileLock.unlock()
        }
    }
}
