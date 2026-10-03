package com.holymanzion.simplenotepro.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.math.max

/** A stored image: its file name inside [AttachmentStore.dir] and pixel size. */
data class StoredImage(val fileName: String, val width: Int, val height: Int)

/**
 * Keeps note images as JPEGs in app-private storage. Imports are downscaled so a
 * 50 MP photo doesn't cost 20 MB per note, and rotated per EXIF so they display upright.
 */
class AttachmentStore(private val context: Context) {
    val dir: File = directory(context).apply { mkdirs() }

    fun file(fileName: String) = File(dir, fileName)

    /** Decodes, downscales and saves the image at [uri]. Runs on the caller's thread. */
    fun import(uri: Uri): StoredImage {
        val bitmap = fitWithinMax(decode(uri))
        try {
            return save(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /** Stores raw image bytes (from a backup) under a fresh name. */
    fun importBytes(input: InputStream, extension: String = "jpg"): String {
        val name = "${UUID.randomUUID()}.$extension"
        file(name).outputStream().use { input.copyTo(it) }
        return name
    }

    fun copy(fileName: String): String {
        val name = "${UUID.randomUUID()}.${fileName.substringAfterLast('.', "jpg")}"
        file(fileName).copyTo(file(name))
        return name
    }

    fun delete(fileNames: Collection<String>) {
        fileNames.forEach { file(it).delete() }
    }

    /** Removes files no attachment row points at (left behind by a crash mid-import). */
    fun deleteOrphans(referenced: Set<String>) {
        dir.listFiles()?.filter { it.name !in referenced }?.forEach { it.delete() }
    }

    /** Largest power of two that keeps the longest side at or above the limit. */
    private fun sampleSizeFor(longest: Int): Int {
        var sample = 1
        while (longest / (sample * 2) >= MAX_DIMENSION) sample *= 2
        return sample
    }

    /** Sampling only halves, so the decoded image can be up to 2x over the limit. */
    private fun fitWithinMax(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_DIMENSION) return bitmap
        val scale = MAX_DIMENSION.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        bitmap.recycle()
        return scaled
    }

    private fun save(bitmap: Bitmap): StoredImage {
        val name = "${UUID.randomUUID()}.jpg"
        file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        return StoredImage(name, bitmap.width, bitmap.height)
    }

    private fun decode(uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies EXIF orientation itself.
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // A sample size shrinks both sides by the same factor, so it can't distort the
                // picture whichever way EXIF rotates it. (An exact target size would have to
                // match the rotated width/height; getting that wrong stretches camera photos.)
                // fitWithinMax() then trims to the exact limit.
                decoder.setTargetSampleSize(sampleSizeFor(max(info.size.width, info.size.height)))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = sampleSizeFor(max(bounds.outWidth, bounds.outHeight))
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Can't read image")
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        bitmap.recycle()
        return rotated
    }

    companion object {
        fun directory(context: Context) = File(context.filesDir, "attachments")

        /** Where an attachment lives, for UI code that only has a Context. */
        fun file(context: Context, fileName: String) = File(directory(context), fileName)

        private const val MAX_DIMENSION = 2048
        private const val JPEG_QUALITY = 85
    }
}
