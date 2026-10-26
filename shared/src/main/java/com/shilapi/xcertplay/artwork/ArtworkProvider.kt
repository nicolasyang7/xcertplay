package com.shilapi.xcertplay.artwork

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Cross-process artwork content provider for launcher widgets and cluster HUD.
 * Exposes content://com.shilapi.xcertplay.artwork/current
 */
class ArtworkProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.shilapi.xcertplay.artwork"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/current")
        private const val ARTWORK_FILENAME = "current_artwork.jpg"
        private const val TAG = "xcertplay-artwork-prov"

        fun updateArtwork(context: Context, bytes: ByteArray) {
            try {
                val file = File(context.cacheDir, ARTWORK_FILENAME)
                FileOutputStream(file).use { it.write(bytes) }
                context.contentResolver.notifyChange(CONTENT_URI, null)
                Log.i(TAG, "Updated current artwork file bytes=${bytes.size}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write artwork file", e)
            }
        }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: return null
        val file = File(ctx.cacheDir, ARTWORK_FILENAME)
        if (!file.exists()) return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/jpeg"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, sa: Array<out String>?, so: String?): Cursor? = null
    override fun insert(uri: Uri, v: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, sa: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, sa: Array<out String>?): Int = 0
}
