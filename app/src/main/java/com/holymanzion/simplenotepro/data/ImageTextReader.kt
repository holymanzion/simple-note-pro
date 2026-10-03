package com.holymanzion.simplenotepro.data

import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads printed text (receipts, documents, whiteboards, screenshots) from a stored
 * picture, entirely on the phone with ML Kit's bundled Latin-script model: works
 * offline, and no picture ever leaves the device.
 */
class ImageTextReader {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** The text in [file], "" if there is none. Throws if the recognizer itself fails. */
    suspend fun read(file: File): String {
        val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path) } ?: return ""
        try {
            // Stored pictures are already upright (see AttachmentStore), so rotation is 0.
            return recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text.trim()
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
