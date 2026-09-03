package com.craftworks.music.managers

import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    private const val TAG = "CHORA_DEBUG"
    private const val ENABLE_FILE_LOGGING = false

    @Synchronized
    fun log(section: String, message: String) {
        if (!ENABLE_FILE_LOGGING) return

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logLine = "$timestamp [$section] $message\n"
        Log.d(TAG, "[$section] $message")

        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val logFile = File(downloadDir, "chora_debug_log.txt")
            logFile.appendText(logLine)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to Download log file: ${e.message}")
        }
    }
}
