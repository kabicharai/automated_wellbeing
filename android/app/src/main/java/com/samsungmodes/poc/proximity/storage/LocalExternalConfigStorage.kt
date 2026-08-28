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
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, jsonContent)
            } else {
                saveViaLegacyFileSystem(jsonContent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Reads the backup JSON if present on the device.
     */
    fun readBackup(context: Context): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                readViaMediaStore(context) ?: readViaLegacyFileSystem()
            } else {
                readViaLegacyFileSystem()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveViaMediaStore(context: Context, jsonContent: String): Boolean {
        val resolver = context.contentResolver

        // Check if file already exists in Documents/SamsungModes
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(FILE_NAME, "%${FOLDER_NAME}%")

        val queryUri = MediaStore.Files.getContentUri("external")
        var existingUri: Uri? = null

        resolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                existingUri = Uri.withAppendedPath(queryUri, id.toString())
            }
        }

        val targetUri = existingUri ?: run {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$FOLDER_NAME")
            }
            resolver.insert(queryUri, values)
        } ?: return false

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
