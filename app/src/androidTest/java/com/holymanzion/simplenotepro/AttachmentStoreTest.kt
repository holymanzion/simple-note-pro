package com.holymanzion.simplenotepro

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.holymanzion.simplenotepro.data.AttachmentStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Photos from a phone camera are usually stored sideways with an EXIF "rotate" tag.
 * These tests build such files and check the stored image comes out upright and
 * undistorted, both below and above the downscale limit.
 */
@RunWith(AndroidJUnit4::class)
class AttachmentStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = AttachmentStore(context)

    /** Left half red, right half blue, saved as JPEG with the given EXIF orientation. */
    private fun sidewaysPhoto(width: Int, height: Int, orientation: Int): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
            drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
        }
        val file = File(context.cacheDir, "exif-${System.nanoTime()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return Uri.fromFile(file)
    }

    private fun isRed(pixel: Int) = Color.red(pixel) > 200 && Color.blue(pixel) < 60
    private fun isBlue(pixel: Int) = Color.blue(pixel) > 200 && Color.red(pixel) < 60

    private fun checkRotated90(width: Int, height: Int) {
        // Sensor image is landscape; "rotate 90° clockwise" makes it portrait with red on top.
        val stored = store.import(sidewaysPhoto(width, height, ExifInterface.ORIENTATION_ROTATE_90))
        val decoded = BitmapFactory.decodeFile(store.file(stored.fileName).path)
        Log.i("QA", "EXIF90 ${width}x$height -> stored ${stored.width}x${stored.height}, file ${decoded.width}x${decoded.height}")
        assertEquals("recorded size matches the file", decoded.width to decoded.height, stored.width to stored.height)
        assertTrue("portrait after rotation, got ${decoded.width}x${decoded.height}", decoded.height > decoded.width)
        assertEquals("aspect ratio kept", height.toFloat() / width, decoded.width.toFloat() / decoded.height, 0.02f)
        assertTrue("top is red", isRed(decoded.getPixel(decoded.width / 2, decoded.height / 4)))
        assertTrue("bottom is blue", isBlue(decoded.getPixel(decoded.width / 2, decoded.height * 3 / 4)))
        store.delete(listOf(stored.fileName))
    }

    @Test fun smallRotatedPhoto_isUprightAndUndistorted() = checkRotated90(1600, 1200)

    @Test fun largeRotatedPhoto_isDownscaledUprightAndUndistorted() = checkRotated90(4000, 3000)

    @Test fun unrotatedLargePhoto_keepsShape() {
        val stored = store.import(sidewaysPhoto(4000, 3000, ExifInterface.ORIENTATION_NORMAL))
        assertEquals(2048, stored.width)
        assertEquals(1536, stored.height)
        store.delete(listOf(stored.fileName))
    }
}
