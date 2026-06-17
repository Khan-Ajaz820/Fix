package com.amosix.emojimosaic

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Exports mosaic bitmaps to the device gallery.
 * Matches the web app's download functionality:
 * - Standard (2K): 62% of full size, JPEG quality 85%
 * - Ultra HD (4K): Full size, PNG
 */
object MosaicExporter {

    private const val STANDARD_SCALE = 0.62f
    private const val JPEG_QUALITY = 85

    /**
     * Save mosaic as Standard quality (2K).
     * Matches web: 62% scale, JPEG quality 0.85
     */
    fun saveStandard(context: Context, bitmap: Bitmap): Uri? {
        val scaledWidth = (bitmap.width * STANDARD_SCALE).toInt()
        val scaledHeight = (bitmap.height * STANDARD_SCALE).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        val uri = saveBitmapToGallery(
            context,
            scaled,
            "mosaic-standard-${System.currentTimeMillis()}.jpg",
            Bitmap.CompressFormat.JPEG,
            JPEG_QUALITY
        )
        scaled.recycle()
        return uri
    }

    /**
     * Save mosaic as Ultra HD quality (4K).
     * Matches web: full size, PNG
     */
    fun saveUltraHD(context: Context, bitmap: Bitmap): Uri? {
        return saveBitmapToGallery(
            context,
            bitmap,
            "mosaic-ultra-hd-${System.currentTimeMillis()}.png",
            Bitmap.CompressFormat.PNG,
            100
        )
    }

    /**
     * Save bitmap to device gallery using MediaStore (API 29+)
     * or direct file write (API 28 and below).
     */
    private fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        filename: String,
        format: Bitmap.CompressFormat,
        quality: Int
    ): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ : Use MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(
                    MediaStore.Images.Media.MIME_TYPE,
                    if (format == Bitmap.CompressFormat.PNG) "image/png" else "image/jpeg"
                )
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/EmojiMosaic"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                val outputStream: OutputStream? = resolver.openOutputStream(it)
                outputStream?.use { stream ->
                    bitmap.compress(format, quality, stream)
                }
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)
            }

            uri
        } else {
            // Android 9 and below: Direct file write
            val picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            )
            val mosaicDir = File(picturesDir, "EmojiMosaic")
            if (!mosaicDir.exists()) mosaicDir.mkdirs()

            val file = File(mosaicDir, filename)
            FileOutputStream(file).use { stream ->
                bitmap.compress(format, quality, stream)
            }

            // Notify gallery
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(
                    MediaStore.Images.Media.MIME_TYPE,
                    if (format == Bitmap.CompressFormat.PNG) "image/png" else "image/jpeg"
                )
            }
            context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            )
        }
    }
}
