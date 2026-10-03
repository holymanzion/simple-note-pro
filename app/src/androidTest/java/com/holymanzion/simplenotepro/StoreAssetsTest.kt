package com.holymanzion.simplenotepro

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.WindowInsets
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.holymanzion.simplenotepro.data.AppSettings
import com.holymanzion.simplenotepro.data.ChecklistCodec
import com.holymanzion.simplenotepro.data.ChecklistItem
import com.holymanzion.simplenotepro.data.Note
import com.holymanzion.simplenotepro.data.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Generates the Play Store graphics from the real app, so they always match it:
 * the 512 px icon, the 1024x500 feature graphic and captioned 1080x1920 screenshots.
 * Output goes to files/store (pull with `adb exec-out run-as … tar`).
 * Not a test of behaviour, so it's run on its own, not with the QA suite.
 */
@RunWith(AndroidJUnit4::class)
class StoreAssetsTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val container get() = (rule.activity.application as SimpleNoteApp).container
    private val outDir get() = File(rule.activity.filesDir, "store").apply { mkdirs() }

    private val brandAmber = Color.rgb(0xF4, 0xB4, 0x00)
    private val ink = Color.rgb(0x2B, 0x20, 0x08)
    private val cream = Color.rgb(0xFF, 0xF4, 0xDC)

    @Test
    fun generate() {
        icon()
        featureGraphic()
        screenshots()
    }

    // --- Icon and feature graphic ---

    /** The launcher icon, full-bleed: Play applies its own rounded mask. */
    private fun drawIcon(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(brandAmber)
        ContextCompat.getDrawable(rule.activity, R.drawable.ic_launcher_foreground)!!.apply {
            setBounds(0, 0, size, size)
            draw(canvas)
        }
        return bitmap
    }

    private fun icon() = save(drawIcon(512), "icon-512.png")

    private fun featureGraphic() {
        val w = 1024
        val h = 500
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Color.rgb(0xFF, 0xD2, 0x5E), Color.rgb(0xF0, 0xA2, 0x00), Shader.TileMode.CLAMP)
        })
        // Soft note shapes in the background for texture.
        val sheet = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 255, 255, 255) }
        canvas.drawRoundRect(RectF(700f, -40f, 960f, 200f), 36f, 36f, sheet)
        canvas.drawRoundRect(RectF(820f, 260f, 1080f, 540f), 36f, 36f, sheet)

        // Icon, with the rounded-square shape Android launchers use.
        val iconSize = 260
        val iconLeft = 90f
        val iconTop = (h - iconSize) / 2f
        val clip = Path().apply { addRoundRect(RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize), 64f, 64f, Path.Direction.CW) }
        canvas.drawRoundRect(RectF(iconLeft, iconTop + 10, iconLeft + iconSize, iconTop + iconSize + 10), 64f, 64f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 60, 40, 0) })
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(drawIcon(iconSize), iconLeft, iconTop, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()

        val textLeft = iconLeft + iconSize + 56
        canvas.drawText("Simple Note Pro", textLeft, 218f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; textSize = 66f; typeface = Typeface.create("sans-serif", Typeface.BOLD)
        })
        val tagline = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x4A, 0x37, 0x08); textSize = 34f; typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        canvas.drawText("Notes, checklists, reminders", textLeft, 282f, tagline)
        canvas.drawText("and photos. Private by design.", textLeft, 328f, tagline)
        save(bitmap, "feature-graphic.png")
    }

    // --- Screenshots ---

    private fun screenshots() {
        seedDemoNotes()
        val theme = { mode: ThemeMode -> container.settings.update { it.copy(themeMode = mode) }; rule.waitForIdle() }

        theme(ThemeMode.LIGHT)
        waitForText("Weekend in Lisbon")
        shot("01-notes", "All your notes at a glance", "Colours, labels and pinned notes")

        openNote("Book club: October")
        shot("02-formatting", "Format as you write", "Headings, lists, bold and links")
        back()

        openNote("Weekend in Lisbon")
        shot("03-checklist", "Checklists you can reorder", "Drag items; done ones move down")
        back()

        openNote("Water the plants")
        rule.onNodeWithContentDescription("Reminder").performClick()
        waitForText("Repeat")
        shot("04-reminders", "Reminders that repeat", "Daily, weekly, monthly or yearly")
        rule.onNodeWithText("Cancel").performClick()
        back()

        waitForText("Weekend in Lisbon")
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("roller")
        waitForText("Found in image")
        hideKeyboard()
        shot("05-search-photos", "Find words inside photos", "Receipts and documents become searchable")
        rule.onNodeWithContentDescription("Clear search").performClick()
        hideKeyboard()

        theme(ThemeMode.DARK)
        waitForText("Weekend in Lisbon")
        shot("06-dark", "Light or dark", "Easy on the eyes, day and night")

        rule.onNodeWithContentDescription("Open menu").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Settings").onLast().performClick()
        waitForText("App lock")
        shot("07-private", "Private by design", "App lock and automatic backups. No account, no ads.")
        theme(ThemeMode.LIGHT)
    }

    private fun seedDemoNotes() = runBlocking {
        val repo = container.repository
        repo.deleteForever(repo.getAll().map { it.note.id })
        repo.labels.first().forEach { repo.deleteLabel(it.id) }
        container.settings.update { AppSettings(themeMode = ThemeMode.LIGHT) }
        container.appLock.promptAutomatically = false
        container.appLock.unlock()

        val travel = repo.createLabel("Travel")!!
        val ideas = repo.createLabel("Ideas")!!
        val home = repo.createLabel("Home")!!
        val receipts = repo.createLabel("Receipts")!!
        val zone = ZoneId.systemDefault()
        fun at(daysFromNow: Long, hour: Int) =
            LocalDate.now(zone).plusDays(daysFromNow).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        // Oldest first, so the newest ends up at the top of the grid.
        var time = System.currentTimeMillis() - 3_600_000L
        suspend fun add(note: Note, vararg labels: Long): Long {
            time += 60_000
            return repo.save(note.copy(createdAt = time, updatedAt = time), labels.toList())
        }
        fun list(vararg items: Pair<String, Boolean>) =
            ChecklistCodec.encode(items.mapIndexed { i, (text, done) -> ChecklistItem(i + 1L, text, done) })

        val receipt = add(Note(title = "Hardware store", content = "Paint for the hallway", color = 10), receipts)
        repo.addImage(receipt, receiptImage())
        add(Note(title = "Project ideas", content = "- Herb planter for the balcony\n- Weekend photo walk\n- Learn three songs on guitar", color = 6), ideas)
        add(Note(title = "Water the plants", content = "Ferns on Sunday, cactus once a month", color = 5, reminderAt = at(2, 8), reminderRepeat = "WEEKLY"), home)
        add(Note(title = "Groceries", isChecklist = true, color = 4,
            checklist = list("Oat milk" to false, "Eggs" to false, "Spinach" to false, "Lemons" to false, "Coffee beans" to true)), home)
        add(Note(title = "Dentist", content = "Bring the new insurance card", color = 1, reminderAt = at(1, 9)))
        add(Note(title = "Book club: October", color = 8, content =
            "# The Midnight Library\n- Meet at Ana's, **Thursday 7 pm**\n- Bring snacks to share\n- Next pick: vote in the group chat\n\nReading notes: https://example.com/bookclub"), ideas)
        add(Note(title = "Weekend in Lisbon", isChecklist = true, isPinned = true, color = 3,
            checklist = list("Passport" to true, "Phone charger" to false, "Sunscreen" to false, "Book for the flight" to false, "Tram day pass" to false)), travel)
        repo.readPendingImageText()
    }

    /** A plain printed receipt for the text-in-photos screenshot (fictional shop and prices). */
    private fun receiptImage(): Uri {
        val bitmap = Bitmap.createBitmap(1000, 1200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(250, 248, 242)) }
        val canvas = Canvas(bitmap)
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 30, 30); textSize = 64f; typeface = Typeface.MONOSPACE }
        val normal = Paint(big).apply { textSize = 50f }
        canvas.drawText("CORNER HARDWARE", 120f, 160f, big)
        listOf("Paint roller      8.90", "Wall paint 2L     24.50", "Masking tape      3.10", "", "TOTAL            36.50")
            .forEachIndexed { i, line -> canvas.drawText(line, 100f, 320f + i * 110f, normal) }
        val file = File(rule.activity.cacheDir, "demo-receipt.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }

    private fun waitForText(text: String) {
        rule.waitUntil(8_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openNote(title: String) {
        waitForText(title)
        rule.onAllNodesWithText(title)[0].performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }

    private fun back() = rule.onNodeWithContentDescription("Back").performClick()

    private fun hideKeyboard() {
        rule.runOnUiThread {
            val window = rule.activity.window
            WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
        rule.waitForIdle()
        Thread.sleep(600)
    }

    /** Screen capture without the status and navigation bars, framed with a caption. */
    private fun shot(name: String, title: String, subtitle: String) {
        rule.waitForIdle()
        Thread.sleep(900) // let animations, images and ripples settle
        val raw = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val insets = rule.activity.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.systemBars())
        val cropped = Bitmap.createBitmap(raw, 0, insets.top, raw.width, raw.height - insets.top - insets.bottom)
        save(frame(cropped, title, subtitle), "screenshots/$name.png")
    }

    private fun frame(screen: Bitmap, title: String, subtitle: String): Bitmap {
        val w = 1080
        val h = 1920
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), cream, Color.rgb(0xFF, 0xE1, 0x9C), Shader.TileMode.CLAMP)
        })
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; textSize = 76f; typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x5C, 0x46, 0x10); textSize = 42f; typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        fun layout(text: String, paint: TextPaint) =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, w - 120).setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        val titleLayout = layout(title, titlePaint)
        val subLayout = layout(subtitle, subPaint)
        canvas.save(); canvas.translate(60f, 90f); titleLayout.draw(canvas); canvas.restore()
        canvas.save(); canvas.translate(60f, 90f + titleLayout.height + 18f); subLayout.draw(canvas); canvas.restore()

        // The phone screen, scaled to fill the space below the caption, with rounded corners.
        val top = 90f + titleLayout.height + 18f + subLayout.height + 60f
        val maxH = h - top - 70f
        val scale = minOf(maxH / screen.height, (w - 160f) / screen.width)
        val sw = screen.width * scale
        val sh = screen.height * scale
        val left = (w - sw) / 2
        val rect = RectF(left, top, left + sw, top + sh)
        canvas.drawRoundRect(RectF(rect.left, rect.top + 14, rect.right, rect.bottom + 14), 44f, 44f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 80, 50, 0) })
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, 44f, 44f, Path.Direction.CW) })
        canvas.drawBitmap(screen, null, rect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restore()
        canvas.drawRoundRect(rect, 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(60, 60, 40, 0)
        })
        return out
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File(outDir, name).apply { parentFile?.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
