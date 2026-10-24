package com.kappi.crunch.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * A downloaded file is identified either by a MediaStore content URI
 * ("content://…") or, on legacy storage and for staging files, by an
 * absolute filesystem path.
 */
object FileLocation {

    fun isContentUri(path: String): Boolean = path.startsWith("content://")

    fun toUri(context: Context, path: String): Uri? {
        if (path.isEmpty()) return null
        if (isContentUri(path)) return Uri.parse(path)
        val file = File(path)
        if (!file.exists()) return null
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            null
        }
    }

    fun exists(context: Context, path: String): Boolean {
        if (path.isEmpty()) return false
        if (!isContentUri(path)) return File(path).exists()
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(path), "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    fun delete(context: Context, path: String): Boolean {
        if (path.isEmpty()) return false
        if (!isContentUri(path)) return File(path).delete()
        return runCatching {
            context.contentResolver.delete(Uri.parse(path), null, null) > 0
        }.getOrDefault(false)
    }
}
