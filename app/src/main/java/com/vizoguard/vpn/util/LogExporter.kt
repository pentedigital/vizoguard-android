package com.vizoguard.vpn.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object LogExporter {
    fun exportLogs(context: Context): Intent? {
        val logDir = File(context.filesDir, "logs")
        val logFiles = logDir.listFiles()?.filter { it.name.startsWith("vizoguard") } ?: return null
        if (logFiles.isEmpty()) return null

        // Zip all log files
        val zipFile = File(context.cacheDir, "vizoguard-logs.zip")
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
                logFiles.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } catch (e: Exception) {
            zipFile.delete()
            throw e
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Vizoguard VPN Logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
