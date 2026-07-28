package com.splitcruiser.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Turns a picked image into JPEG bytes the shared storage client can upload.
 *
 * Decoding and scaling stay on Android because they are `android.graphics`; the shared client takes
 * a `ByteArray` so iOS can hand it a `UIImage`'s JPEG data through the same path.
 */
object ProfileImages {

    private const val MAX_EDGE = 512
    private const val JPEG_QUALITY = 85

    suspend fun readResizedJpeg(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val original = context.contentResolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@runCatching null

            val aspectRatio = original.width.toFloat() / original.height.toFloat()
            val (width, height) = if (aspectRatio > 1) {
                MAX_EDGE to (MAX_EDGE / aspectRatio).toInt().coerceAtLeast(1)
            } else {
                (MAX_EDGE * aspectRatio).toInt().coerceAtLeast(1) to MAX_EDGE
            }

            val resized = Bitmap.createScaledBitmap(original, width, height, true)
            val bytes = ByteArrayOutputStream().use { out ->
                resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }

            original.recycle()
            if (resized !== original) resized.recycle()
            bytes
        }.onFailure {
            Log.w("SplitCruiser", "Could not read the picked image", it)
        }.getOrNull()
    }
}
