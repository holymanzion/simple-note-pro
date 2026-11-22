package com.holymanzion.simplenotepro.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnitType
import com.holymanzion.simplenotepro.data.ChecklistItem
import com.holymanzion.simplenotepro.text.RichText
import com.holymanzion.simplenotepro.ui.theme.NoteColors
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/** What a shared picture or PDF shows: the note as written, on its light color. */
data class ExportedNote(
    val title: String,
    val content: String,
    val isChecklist: Boolean,
    val items: List<ChecklistItem>,
    val color: Int,
    val images: List<File>,
)

/**
 * Draws a note as a PNG or a PDF for sharing. Always the light colors, so it reads well
 * when printed or opened on someone else's phone whatever theme this one uses.
 */
object NoteExport {
    private const val INK = 0xFF1F1B16.toInt()
    private const val FAINT = 0xFF6F6659.toInt()
    private const val LINK = 0xFF7A5900.toInt()

    // Picture: phone-screen width, with a cap so a very long note can't use all the memory.
    private const val IMAGE_WIDTH = 1080
    private const val IMAGE_PADDING = 72
    private const val IMAGE_MAX_HEIGHT = 8000

    // PDF: A4 in points.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val PAGE_MARGIN = 48

    private const val FOOTER = "Made with Simple Note Pro"

    private fun background(note: ExportedNote): Int {
        val light = NoteColors.getOrNull(note.color)?.light
        return if (light == null || light == androidx.compose.ui.graphics.Color.Unspecified) Color.WHITE else light.toArgb()
    }

    /** The body as styled Android text: rendered formatting, or checklist lines with boxes. */
    internal fun body(note: ExportedNote): CharSequence {
        if (!note.isChecklist) return toSpanned(RichText.render(note.content.trimEnd(), androidx.compose.ui.graphics.Color(LINK)))
        val out = SpannableStringBuilder()
        // Unticked first, like the editor with "move checked items to bottom".
        val ordered = note.items.filter { it.text.isNotBlank() }.sortedBy { it.checked }
        ordered.forEachIndexed { i, item ->
            if (i > 0) out.append('\n')
            val start = out.length
            out.append(if (item.checked) "☑  " else "☐  ").append(item.text)
            if (item.checked) {
                out.setSpan(StrikethroughSpan(), start + 3, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(ForegroundColorSpan(FAINT), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return out
    }

    /** Converts the styles [RichText.render] uses into Android spans. */
    internal fun toSpanned(text: AnnotatedString): Spanned {
        val out = SpannableStringBuilder(text.text)
        text.spanStyles.forEach { range ->
            val s = range.item
            fun span(what: Any) = out.setSpan(what, range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val bold = s.fontWeight != null && s.fontWeight!! >= FontWeight.SemiBold
            val italic = s.fontStyle == FontStyle.Italic
            when {
                bold && italic -> span(StyleSpan(Typeface.BOLD_ITALIC))
                bold -> span(StyleSpan(Typeface.BOLD))
                italic -> span(StyleSpan(Typeface.ITALIC))
            }
            s.textDecoration?.let { decoration ->
                if (decoration.contains(TextDecoration.LineThrough)) span(StrikethroughSpan())
                if (decoration.contains(TextDecoration.Underline)) span(UnderlineSpan())
            }
            if (s.color != androidx.compose.ui.graphics.Color.Unspecified) span(ForegroundColorSpan(s.color.toArgb()))
            if (s.fontSize.type == TextUnitType.Em) span(RelativeSizeSpan(s.fontSize.value))
        }
        return out
    }

    private fun layout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.25f)
            .setIncludePad(false)
            .build()

    private fun paints(density: Float) = Triple(
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 26f * density; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) },
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 17f * density },
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = FAINT; textSize = 11f * density },
    )

    /** Decodes [file] at roughly [width] pixels wide, or null if it can't be read. */
    private fun decodeFor(file: File, width: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun shareDir(context: Context) = File(context.cacheDir, "share").apply {
        mkdirs()
        listFiles()?.forEach { it.delete() } // only the latest share is needed
    }

    private fun fileName(note: ExportedNote, extension: String): String {
        val base = note.title.trim().replace(Regex("""[\\/:*?"<>|\n]+"""), " ").take(60).trim().ifEmpty { "Note" }
        return "$base.$extension"
    }

    /** One tall picture of the note (pictures included), cut off with "…" if extremely long. */
    fun toImage(context: Context, note: ExportedNote): File {
        val density = IMAGE_WIDTH / 400f
        val (titlePaint, bodyPaint, footerPaint) = paints(density)
        val width = IMAGE_WIDTH - 2 * IMAGE_PADDING
        val title = note.title.trim().takeIf { it.isNotEmpty() }?.let { layout(it, titlePaint, width) }
        val bodyText = body(note)
        val body = bodyText.takeIf { it.isNotBlank() }?.let { layout(it, bodyPaint, width) }
        val footer = layout(FOOTER, footerPaint, width)
        val gap = (16 * density).roundToInt()

        val images = note.images.mapNotNull { file -> decodeFor(file, width) }
        val imageHeights = images.map { (it.height * width.toFloat() / it.width).roundToInt() }

        val fixed = 2 * IMAGE_PADDING + (title?.height?.plus(gap) ?: 0) + gap + footer.height
        val bodyHeight = body?.height ?: 0
        // Keep as much of the body as fits; pictures come after it and are dropped first.
        val ellipsis = bodyPaint.fontSpacing.roundToInt()
        val room = IMAGE_MAX_HEIGHT - fixed
        // If it has to be cut, leave room for the "…" line too.
        val bodyShown = if (bodyHeight <= room) bodyHeight else room - ellipsis
        val lastLine = body?.let { b -> (0 until b.lineCount).lastOrNull { b.getLineBottom(it) <= bodyShown } ?: -1 } ?: -1
        val truncated = body != null && lastLine < body.lineCount - 1
        val bodyDrawn = if (body == null) 0 else if (lastLine < 0) 0 else body.getLineBottom(lastLine)
        var height = fixed + bodyDrawn + (if (truncated) ellipsis else 0)
        val shownImages = mutableListOf<Int>()
        if (!truncated) {
            imageHeights.forEachIndexed { i, h ->
                if (height + gap + h <= IMAGE_MAX_HEIGHT) {
                    shownImages += i
                    height += gap + h
                }
            }
        }

        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(background(note))
            var y = IMAGE_PADDING.toFloat()
            title?.let {
                canvas.save(); canvas.translate(IMAGE_PADDING.toFloat(), y); it.draw(canvas); canvas.restore()
                y += it.height + gap
            }
            if (body != null && bodyDrawn > 0) {
                canvas.save()
                canvas.translate(IMAGE_PADDING.toFloat(), y)
                canvas.clipRect(0, 0, width, bodyDrawn)
                body.draw(canvas)
                canvas.restore()
                y += bodyDrawn
            }
            if (truncated) {
                canvas.drawText("…", IMAGE_PADDING.toFloat(), y + bodyPaint.fontSpacing * 0.8f, bodyPaint)
                y += bodyPaint.fontSpacing
            }
            val radius = 12 * density
            shownImages.forEach { i ->
                y += gap
                val dest = RectF(IMAGE_PADDING.toFloat(), y, (IMAGE_PADDING + width).toFloat(), y + imageHeights[i])
                canvas.save()
                canvas.clipPath(android.graphics.Path().apply { addRoundRect(dest, radius, radius, android.graphics.Path.Direction.CW) })
                canvas.drawBitmap(images[i], null, dest, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restore()
                y += imageHeights[i]
            }
            y += gap
            canvas.save(); canvas.translate(IMAGE_PADDING.toFloat(), y); footer.draw(canvas); canvas.restore()

            val file = File(shareDir(context), fileName(note, "png"))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return file
        } finally {
            bitmap.recycle()
            images.forEach { it.recycle() }
        }
    }

    /** An A4 PDF, split over as many pages as the note needs, pictures included. */
    fun toPdf(context: Context, note: ExportedNote): File {
        val density = 0.75f // 17 "dp" body text becomes about 12.75 pt
        val (titlePaint, bodyPaint, footerPaint) = paints(density)
        val width = PAGE_WIDTH - 2 * PAGE_MARGIN
        val bottom = PAGE_HEIGHT - PAGE_MARGIN
        val gap = 12f
        val document = PdfDocument()
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        val pageBackground = background(note)

        fun newPage(): Canvas {
            page?.let { document.finishPage(it) }
            pageNumber++
            val p = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            page = p
            p.canvas.drawColor(pageBackground)
            footerPaint.let { fp ->
                p.canvas.drawText("$FOOTER · $pageNumber", PAGE_MARGIN.toFloat(), (PAGE_HEIGHT - PAGE_MARGIN / 2).toFloat(), fp)
            }
            y = PAGE_MARGIN.toFloat()
            return p.canvas
        }

        try {
            var canvas = newPage()
            note.title.trim().takeIf { it.isNotEmpty() }?.let { text ->
                val title = layout(text, titlePaint, width)
                canvas.save(); canvas.translate(PAGE_MARGIN.toFloat(), y); title.draw(canvas); canvas.restore()
                y += title.height + gap
            }
            val bodyText = body(note)
            if (bodyText.isNotBlank()) {
                val body = layout(bodyText, bodyPaint, width)
                var line = 0
                while (line < body.lineCount) {
                    // As many whole lines as fit on this page.
                    val top = body.getLineTop(line)
                    var last = line
                    while (last + 1 < body.lineCount && y + (body.getLineBottom(last + 1) - top) <= bottom) last++
                    if (y + (body.getLineBottom(line) - top) > bottom) { canvas = newPage(); continue }
                    val sliceHeight = body.getLineBottom(last) - top
                    canvas.save()
                    canvas.translate(PAGE_MARGIN.toFloat(), y - top)
                    canvas.clipRect(0, top, width, top + sliceHeight)
                    body.draw(canvas)
                    canvas.restore()
                    y += sliceHeight
                    line = last + 1
                    if (line < body.lineCount) canvas = newPage()
                }
            }
            note.images.forEach { file ->
                val image = decodeFor(file, width * 2) ?: return@forEach
                try {
                    var w = width.toFloat()
                    var h = image.height * w / image.width
                    val maxHeight = (bottom - PAGE_MARGIN).toFloat()
                    if (h > maxHeight) { w *= maxHeight / h; h = maxHeight }
                    if (y + gap + h > bottom) canvas = newPage() else y += gap
                    canvas.drawBitmap(image, null, RectF(PAGE_MARGIN.toFloat(), y, PAGE_MARGIN + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
                    y += h
                } finally {
                    image.recycle()
                }
            }
            page?.let { document.finishPage(it) }
            page = null
            val file = File(shareDir(context), fileName(note, "pdf"))
            file.outputStream().use { document.writeTo(it) }
            return file
        } finally {
            document.close()
        }
    }
}
