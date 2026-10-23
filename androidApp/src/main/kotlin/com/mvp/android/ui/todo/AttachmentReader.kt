package com.mvp.android.ui.todo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.mvp.feature.todo.domain.model.AttachmentType
import com.mvp.feature.todo.domain.model.NewTodoAttachment
import com.mvp.feature.todo.domain.usecase.AddTodoAttachmentUseCase
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Lit le fichier choisi dans le sélecteur de photos ou de fichiers (ADR 0014). Une image est décodée puis
 * réenregistrée en JPEG, 2 048 px au plus : le fichier joint ne garde aucune métadonnée (position GPS,
 * appareil, date) et HEIC / PNG deviennent un format que KMP accepte. Les autres fichiers sont lus tels quels,
 * jusqu'à la taille maximale + 1 octet (le use case refuse ce qui dépasse). Bloquant : à appeler hors du main thread.
 */
class AttachmentReader(private val context: Context) {

    fun read(uri: Uri, todoId: String): NewTodoAttachment {
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val name = displayName(uri).orEmpty()
        return if (mimeType.startsWith("image/")) {
            NewTodoAttachment(todoId, name.withJpegExtension(), AttachmentType.Photo.mimeType, reencodePhoto(uri))
        } else {
            NewTodoAttachment(todoId, name, mimeType, readAtMost(uri, AddTodoAttachmentUseCase.MAX_SIZE_BYTES + 1))
        }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun readAtMost(uri: Uri, limit: Int): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Fichier illisible")
        return input.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            while (output.size() < limit) {
                val read = it.read(buffer, 0, minOf(buffer.size, limit - output.size()))
                if (read < 0) break
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun reencodePhoto(uri: Uri): ByteArray {
        val bitmap = decodePhoto(uri)
        try {
            val output = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) throw IOException("Photo illisible")
            return output.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    /** Android 9+ : ImageDecoder (HEIC compris) applique l'orientation EXIF ; avant, BitmapFactory + rotation à la main. */
    private fun decodePhoto(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val scale = MAX_PHOTO_SIDE.toFloat() / maxOf(info.size.width, info.size.height)
                if (scale < 1f) {
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
                // Bitmap en mémoire classique : un bitmap « hardware » ne se réencode pas.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            decodePhotoLegacy(uri)
        }

    private fun decodePhotoLegacy(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (longestSide <= 0) throw IOException("Photo illisible")
        var sampleSize = 1
        while (longestSide / (sampleSize * 2) >= MAX_PHOTO_SIDE) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Photo illisible")
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val scale = minOf(1f, MAX_PHOTO_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        if (degrees == 0f && scale == 1f) return decoded
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(degrees)
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }
}

/** « IMG_0001.HEIC » → « IMG_0001.jpg » : le contenu est maintenant du JPEG. */
private fun String.withJpegExtension(): String =
    if (isBlank()) AttachmentType.Photo.defaultFileName else "${substringBeforeLast('.')}.jpg"

private const val MAX_PHOTO_SIDE = 2048
private const val JPEG_QUALITY = 85
private const val BUFFER_SIZE = 64 * 1024
