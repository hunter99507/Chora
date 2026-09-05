package com.craftworks.music.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.craftworks.music.managers.BackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RestoreBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.craftworks.music.ACTION_RESTORE_BACKUP") {
            val jsonContent = intent.getStringExtra("json") ?: run {
                val filePath = intent.getStringExtra("file") ?: "/sdcard/Download/chora_backup.json"
                val file = File(filePath)
                if (file.exists()) file.readText() else null
            }

            if (jsonContent.isNullOrBlank()) {
                Log.e("RestoreBackupReceiver", "No backup JSON content or file found")
                return
            }

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = BackupManager.restoreFromJson(context.applicationContext, jsonContent)
                    Log.d("RestoreBackupReceiver", "Restore result: ${result.getOrNull()}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context.applicationContext, "Backup restored successfully!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("RestoreBackupReceiver", "Restore failed", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
