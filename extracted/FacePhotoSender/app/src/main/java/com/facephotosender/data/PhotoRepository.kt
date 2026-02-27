package com.facephotosender.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

data class GalleryPhoto(val uri: Uri, val dateTaken: Long, val displayName: String)

class PhotoRepository(private val context: Context) {

    companion object {
        private const val TAG = "PhotoRepository"
        private const val LIMIT = 50
    }

    /** Returns the last [LIMIT] photos from the device gallery, newest first */
    fun getRecentPhotos(): List<GalleryPhoto> {
        val photos = mutableListOf<GalleryPhoto>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        val selection = null
        val selectionArgs = null

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

                var count = 0
                while (cursor.moveToNext() && count < LIMIT) {
                    val id   = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)
                    val date = cursor.getLong(dateCol)
                    val uri  = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    photos.add(GalleryPhoto(uri, date, name))
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query gallery", e)
        }

        return photos
    }
}
