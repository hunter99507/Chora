package com.craftworks.music.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.craftworks.music.R
import java.io.File

data class StorageRoot(
    val name: String,
    val file: File,
    val isRemovable: Boolean = false
)

object StorageHelper {
    private const val TAG = "StorageHelper"

    fun getStorageRoots(context: Context): List<StorageRoot> {
        val roots = mutableListOf<StorageRoot>()
        val seenPaths = mutableSetOf<String>()

        fun addRoot(name: String, file: File, isRemovable: Boolean = false) {
            try {
                val canonical = file.canonicalPath
                if (file.exists() && seenPaths.add(canonical)) {
                    roots.add(StorageRoot(name, file, isRemovable))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve canonical path for: ${file.path}", e)
            }
        }

        // 1. Primary Internal Storage (/storage/emulated/0)
        try {
            val primaryExternal = Environment.getExternalStorageDirectory()
            if (primaryExternal != null) {
                addRoot(context.getString(R.string.Storage_Internal), primaryExternal, false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error accessing external storage directory", e)
        }

        // 2. Standard Music Folder
        try {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (musicDir != null && musicDir.exists()) {
                addRoot(context.getString(R.string.Storage_Music), musicDir, false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error accessing music directory", e)
        }

        // 3. Standard Download Folder
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && downloadDir.exists()) {
                addRoot(context.getString(R.string.Storage_Downloads), downloadDir, false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error accessing download directory", e)
        }

        // 4. Secondary external storage volumes (SD Cards, USB drives on Android & Android TV)
        try {
            val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
            for (dir in externalDirs) {
                if (dir != null) {
                    val path = dir.absolutePath
                    val androidIndex = path.indexOf("/Android/data")
                    if (androidIndex > 0) {
                        val rootPath = path.substring(0, androidIndex)
                        val rootFile = File(rootPath)
                        val isInternal = rootPath.contains("emulated") || rootPath == Environment.getExternalStorageDirectory()?.absolutePath
                        val volumeName = if (isInternal) {
                            context.getString(R.string.Storage_Internal)
                        } else {
                            val label = rootFile.name
                            "${context.getString(R.string.Storage_External)} ($label)"
                        }
                        addRoot(volumeName, rootFile, !isInternal)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error discovering external volumes from getExternalFilesDirs", e)
        }

        // 5. Inspect /storage directory directly (common for Android TV USB mounts: /storage/ABCD-1234)
        try {
            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                storageDir.listFiles()?.forEach { file ->
                    val name = file.name
                    if (file.isDirectory && name != "emulated" && name != "self" && name != "knox") {
                        addRoot("${context.getString(R.string.Storage_External)} ($name)", file, true)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error inspecting /storage directory", e)
        }

        // 6. Check SECONDARY_STORAGE env var if present
        try {
            System.getenv("SECONDARY_STORAGE")
                ?.split(":")
                ?.map { File(it) }
                ?.filter { it.exists() }
                ?.forEach { addRoot("${context.getString(R.string.Storage_External)} (${it.name})", it, true) }
        } catch (e: Exception) {
            Log.w(TAG, "Error inspecting SECONDARY_STORAGE", e)
        }

        // Fallback to root / if nothing else found
        if (roots.isEmpty()) {
            roots.add(StorageRoot("Root (/)", File("/"), false))
        }

        return roots
    }

    fun getSubdirectories(dir: File): List<File> {
        return try {
            dir.listFiles()
                ?.filter { it.isDirectory && !it.isHidden && it.canRead() }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to list subdirectories for: ${dir.path}", e)
            emptyList()
        }
    }

    fun parseTreeUriToPath(context: Context, uri: Uri): String? {
        try {
            // Take persistable permission if possible
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Ignore if not supported or not persistent
            }

            val docId = try {
                DocumentsContract.getTreeDocumentId(uri)
            } catch (_: Exception) {
                uri.path ?: ""
            }

            if (docId.contains(":")) {
                val parts = docId.split(":", limit = 2)
                val volume = parts[0]
                val relativePath = parts.getOrNull(1).orEmpty().trimStart('/')

                return if (volume.equals("primary", ignoreCase = true)) {
                    val primaryRoot = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
                    if (relativePath.isNotEmpty()) "$primaryRoot/$relativePath" else primaryRoot
                } else {
                    val volumePath = "/storage/$volume"
                    if (relativePath.isNotEmpty()) "$volumePath/$relativePath" else volumePath
                }
            }

            // Fallback heuristics for raw path
            val rawPath = uri.path ?: return null
            if (rawPath.startsWith("/tree/")) {
                val clean = rawPath.removePrefix("/tree/")
                if (clean.contains(":")) {
                    val split = clean.split(":", limit = 2)
                    val vol = split[0]
                    val sub = split.getOrNull(1).orEmpty().trimStart('/')
                    return if (vol.equals("primary", ignoreCase = true)) {
                        val base = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
                        if (sub.isNotEmpty()) "$base/$sub" else base
                    } else {
                        "/storage/$vol" + (if (sub.isNotEmpty()) "/$sub" else "")
                    }
                }
            }

            return rawPath
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving path from tree URI: $uri", e)
            return null
        }
    }
}
