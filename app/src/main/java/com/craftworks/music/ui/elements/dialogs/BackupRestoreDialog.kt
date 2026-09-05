package com.craftworks.music.ui.elements.dialogs

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craftworks.music.R
import com.craftworks.music.managers.BackupManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupRestoreDialog(
    setShowDialog: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val jsonContent = BackupManager.createBackupJson(context)
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonContent.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.Setting_Export_Success),
                        Toast.LENGTH_LONG
                    ).show()
                    setShowDialog(false)
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.Setting_Backup_Failed) + (e.localizedMessage ?: e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val jsonContent = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: throw IllegalArgumentException("Could not read selected file")

                    val result = BackupManager.restoreFromJson(context, jsonContent)
                    if (result.isSuccess) {
                        Toast.makeText(
                            context,
                            "${context.getString(R.string.Setting_Import_Success)}\n${result.getOrNull()}",
                            Toast.LENGTH_LONG
                        ).show()
                        setShowDialog(false)
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.Setting_Backup_Failed) + (result.exceptionOrNull()?.localizedMessage ?: "Unknown error"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.Setting_Backup_Failed) + (e.localizedMessage ?: e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { setShowDialog(false) },
        icon = {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.s_m_backup_restore),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.Settings_Header_Backup_Restore),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.Setting_Backup_Restore_Description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Export Button
                Button(
                    onClick = {
                        val fileName = "chora_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
                        exportLauncher.launch(fileName)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.rounded_download_24),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.Setting_Export_Backup),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                // Import Button
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_folder_open),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.Setting_Import_Backup),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { setShowDialog(false) }) {
                Text(stringResource(R.string.Action_Close))
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
