package com.samsungmodes.poc.proximity.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader

/**
 * Storage Access & Scoped Storage helper that persists configuration backups to the public
 * Documents/SamsungModes/ directory.
 * Survives full app uninstalls without requiring root or external cloud storage.
 */
object LocalExternalConfigStorage {

    private const val FOLDER_NAME = "SamsungModes"
    private const val FILE_NAME = "samsung_modes_backup.json"
    private const val MIME_TYPE = "application/json"

    /**
     * Saves the backup JSON to standard external public Documents storage.
     */
    fun saveBackup(context: Context, jsonContent: String): Boolean {
        var success = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                success = saveViaMediaStore(context, jsonContent)
            } else {
                success = saveViaLegacyFileSystem(jsonContent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Always also save an internal/app-specific fallback copy
        try {
            val fallbackFile = File(context.filesDir, FILE_NAME)
            FileOutputStream(fallbackFile).use { out ->
                out.write(jsonContent.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return success
    }

    /**
     * Reads the backup JSON if present on the device.
     */
    fun readBackup(context: Context): String? {
        try {
            val content = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                readViaMediaStore(context) ?: readViaLegacyFileSystem()
            } else {
                readViaLegacyFileSystem()
            }
            if (!content.isNullOrBlank()) {
                return content
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Check fallback in app files dir
        try {
            val fallbackFile = File(context.filesDir, FILE_NAME)
            if (fallbackFile.exists() && fallbackFile.isFile) {
                return fallbackFile.readText(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    private fun saveViaMediaStore(context: Context, jsonContent: String): Boolean {
        val resolver = context.contentResolver

        // Check if file already exists in Documents/SamsungModes
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(FILE_NAME, "%${FOLDER_NAME}%")

        val queryUri = MediaStore.Files.getContentUri("external")
        var existingUri: Uri? = null

        try {
            resolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    existingUri = Uri.withAppendedPath(queryUri, id.toString())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (existingUri != null) {
            try {
                resolver.openOutputStream(existingUri!!, "wt")?.use { outputStream ->
                    outputStream.write(jsonContent.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                }
                return true
            } catch (e: Exception) {
                // If opening existingUri fails due to permission mismatch across reinstalls, try deleting and recreating
                try {
                    resolver.delete(existingUri!!, null, null)
                } catch (delEx: Exception) {
                    delEx.printStackTrace()
                }
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$FOLDER_NAME")
        }
        val targetUri = resolver.insert(queryUri, values) ?: return false

        resolver.openOutputStream(targetUri, "wt")?.use { outputStream ->
            outputStream.write(jsonContent.toByteArray(Charsets.UTF_8))
            outputStream.flush()
        }
        return true
    }

    private fun readViaMediaStore(context: Context): String? {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(FILE_NAME, "%${FOLDER_NAME}%")

        val queryUri = MediaStore.Files.getContentUri("external")
        var fileUri: Uri? = null

        resolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                fileUri = Uri.withAppendedPath(queryUri, id.toString())
            }
        }

        fileUri?.let { uri ->
            resolver.openInputStream(uri)?.use { inputStream ->
                return BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
            }
        }
        return null
    }

    private fun saveViaLegacyFileSystem(jsonContent: String): Boolean {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val appFolder = File(docsDir, FOLDER_NAME)
        if (!appFolder.exists()) {
            appFolder.mkdirs()
        }
        val file = File(appFolder, FILE_NAME)
        FileOutputStream(file).use { out ->
            out.write(jsonContent.toByteArray(Charsets.UTF_8))
            out.flush()
        }
        return true
    }

    private fun readViaLegacyFileSystem(): String? {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val file = File(File(docsDir, FOLDER_NAME), FILE_NAME)
        if (file.exists() && file.isFile) {
            FileInputStream(file).use { input ->
                return BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }
        }
        return null
    }
}
