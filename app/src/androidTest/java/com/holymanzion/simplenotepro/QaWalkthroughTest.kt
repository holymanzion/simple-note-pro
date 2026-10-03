package com.holymanzion.simplenotepro

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.holymanzion.simplenotepro.data.AppSettings
import com.holymanzion.simplenotepro.data.ChecklistCodec
import com.holymanzion.simplenotepro.data.ChecklistItem
import com.holymanzion.simplenotepro.data.Note
import com.holymanzion.simplenotepro.data.ThemeMode
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * End-to-end walkthrough of the main user journeys on a real device, with a
 * screenshot at each step (pulled from files/qa afterwards).
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class QaWalkthroughTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val container get() = (rule.activity.application as SimpleNoteApp).container

    @Before
    fun reset() = runBlocking {
        val repo = container.repository
        repo.deleteForever(repo.getAll().map { it.note.id })
        repo.labels.first().forEach { repo.deleteLabel(it.id) }
        container.settings.update { AppSettings(themeMode = ThemeMode.DARK) }
        container.appLock.promptAutomatically = false
        container.appLock.unlock()
        rule.waitForIdle()
    }

    /** A real image file for attachment tests (content doesn't matter, size does). */
    private fun testImage(width: Int = 1200, height: Int = 800, color: Int = android.graphics.Color.rgb(230, 120, 40)): android.net.Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val file = File(rule.activity.cacheDir, "test-${System.nanoTime()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return android.net.Uri.fromFile(file)
    }

    // --- helpers ---

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(400)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(rule.activity.filesDir, "qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private fun waitForText(text: String, substring: Boolean = false, timeout: Long = 5_000) {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitGone(text: String, timeout: Long = 5_000) {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }

    private fun countText(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun seed(vararg notes: Note): List<Long> = runBlocking {
        notes.map { container.repository.save(it, emptyList()) }
    }

    private fun openDrawerItem(label: String) {
        rule.onNodeWithContentDescription("Open menu").performClick()
        rule.waitForIdle()
        // The drawer sheet is composed after the screen content, so it holds the last match.
        rule.onAllNodesWithText(label).onLast().performClick()
        rule.waitForIdle()
    }

    private fun back() = rule.onNodeWithContentDescription("Back").performClick()

    // --- journeys ---

    @Test
    fun t01_createTextNote_savesAndShowsOnHome() {
        shot("01a_home_empty")
        rule.onNodeWithText("Note", useUnmergedTree = true).performClick()
        // Enter in the title jumps to the body.
        rule.onNodeWithTag("editor-title").performTextInput("Meeting notes\n")
        rule.onNodeWithTag("editor-content").assertIsFocused()
        rule.onNodeWithTag("editor-content").performTextInput("Discuss the Q4 roadmap.\nHire two engineers.\nReview budget.")
        // Footer must switch from "New note" once autosave has run.
        waitForText("Edited ", substring = true, timeout = 3_000)
        shot("01b_editor_text")
        back()
        waitForText("Meeting notes")
        rule.onNodeWithText("Discuss the Q4 roadmap", substring = true).assertExists()
        shot("01c_home_one_note")

        // Reopen and check note info
        rule.onNodeWithText("Meeting notes").performClick()
        rule.onNodeWithContentDescription("More").performClick()
        rule.onNodeWithText("Note info").performClick()
        waitForText("Words")
        shot("01d_note_info")
        rule.onNodeWithText("Close").performClick()
        back()

        val notes = runBlocking { container.repository.getAll() }
        assertEquals(1, notes.size)
        assertEquals("Meeting notes", notes.single().note.title)
    }

    @Test
    fun t02_checklist_enterSplitsPasteCreatesItemsAndCheckedMoveDown() {
        rule.onNodeWithContentDescription("New checklist").performClick()
        rule.onNodeWithTag("editor-title").performTextInput("Groceries")
        // Typing then Enter
        rule.onAllNodesWithTag("checklist-item")[0].performTextInput("Milk\n")
        rule.waitForIdle()
        // Pasting several lines at once
        rule.onAllNodesWithTag("checklist-item")[1].performTextInput("Eggs\nBread\nCoffee")
        rule.waitForIdle()
        assertEquals(4, rule.onAllNodesWithTag("checklist-item").fetchSemanticsNodes().size)
        shot("02a_checklist_typed")

        rule.onAllNodes(isToggleable())[0].performClick() // tick Milk
        waitForText("1 checked item")
        shot("02b_checklist_checked")
        back()
        waitForText("Groceries")
        shot("02c_home_checklist_card")

        val items = runBlocking { container.repository.getAll().single().note.items }
        assertEquals(listOf("Milk", "Eggs", "Bread", "Coffee"), items.map { it.text })
        assertTrue(items.first().checked)
    }

    @Test
    fun t03_search_pin_archive_undo() {
        seed(
            Note(title = "Alpha plan", content = "Launch checklist for the new site"),
            Note(title = "Beta recipe", content = "Banana bread with walnuts", color = 4),
            Note(title = "Gamma trip", content = "Pack passport and chargers", color = 7),
        )
        waitForText("Gamma trip")
        shot("03a_home_three_notes")

        rule.onNode(hasSetTextAction()).performTextInput("walnut")
        waitGone("Alpha plan")
        rule.onNodeWithText("Beta recipe").assertExists()
        waitForText("1 result")
        shot("03b_search_results")
        rule.onNodeWithContentDescription("Clear search").performClick()
        waitForText("Alpha plan")

        rule.onNodeWithText("Alpha plan").performTouchInput { longClick() }
        rule.onNodeWithContentDescription("Clear selection").assertExists()
        shot("03c_selection_mode")
        rule.onNodeWithContentDescription("Pin").performClick()
        waitForText("PINNED")
        shot("03d_pinned_section")

        rule.onNodeWithText("Gamma trip").performTouchInput { longClick() }
        rule.onNodeWithContentDescription("Archive").performClick()
        waitForText("Note archived")
        shot("03e_archived_snackbar")
        rule.onNodeWithText("Undo").performClick()
        waitForText("Gamma trip")
    }

    @Test
    fun t04_labels_create_assign_filter() {
        seed(Note(title = "Sprint review", content = "Demo the editor"))
        waitForText("Sprint review")
        rule.onNodeWithContentDescription("Open menu").performClick()
        shot("04a_drawer")
        rule.onNodeWithText("Create new label").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("Work")
        rule.onNode(hasSetTextAction()).performImeAction()
        waitForText("Work")
        shot("04b_labels_screen")
        back()

        rule.onNodeWithText("Sprint review").performClick()
        rule.onNodeWithContentDescription("Labels").performClick()
        rule.onNodeWithText("Work").performClick()
        shot("04c_label_picker")
        rule.onNodeWithText("Done").performClick()
        waitForText("Work")
        back()

        openDrawerItem("Work")
        waitForText("Sprint review")
        shot("04d_label_filter")
        val labels = runBlocking { container.repository.getAll().single().labels }
        assertEquals(listOf("Work"), labels.map { it.name })
    }

    @Test
    fun t05_reminder_setViaPreset_andScheduled() {
        val id = seed(Note(title = "Call the dentist", content = "Ask about Friday")).single()
        waitForText("Call the dentist")
        rule.onNodeWithText("Call the dentist").performClick()
        rule.onNodeWithContentDescription("Reminder").performClick()
        waitForText("Tomorrow morning")
        shot("05a_reminder_dialog")
        rule.onNodeWithText("Pick date & time").performClick()
        waitForText("Next")
        shot("05b_date_picker")
        rule.onNodeWithText("Next").performClick()
        waitForText("Pick a time")
        shot("05c_time_picker")
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Cancel").performClick()
        back()

        // Choosing a preset opens the system notification prompt, which this device won't
        // let tests answer, so set the same reminder through the repository instead.
        val tomorrow8 = java.time.LocalDate.now().plusDays(1).atTime(8, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        runBlocking { container.repository.setReminder(id, tomorrow8) }
        rule.onNodeWithText("Call the dentist").performClick()
        waitForText("Tomorrow, ", substring = true)
        shot("05d_reminder_chip")
        back()

        openDrawerItem("Reminders")
        waitForText("Call the dentist")
        shot("05c_reminders_view")
        val at = runBlocking { container.repository.getAll().single().note.reminderAt }
        assertTrue("reminder should be in the future", at != null && at > System.currentTimeMillis())
    }

    @Test
    fun t06_trash_restore_emptyTrash() {
        seed(Note(title = "Old draft", content = "Obsolete"), Note(title = "Keep me", content = "Still useful"))
        waitForText("Old draft")

        rule.onNodeWithText("Old draft").performTouchInput { longClick() }
        rule.onNodeWithContentDescription("More").performClick()
        rule.onNodeWithText("Delete").performClick()
        waitForText("Note moved to trash")
        waitGone("Old draft")

        openDrawerItem("Trash")
        waitForText("Old draft")
        shot("06a_trash")
        rule.onNodeWithText("Old draft").performClick()
        waitForText("Note in trash")
        shot("06b_trash_dialog")
        rule.onNodeWithText("Restore").performClick()
        waitGone("Old draft")

        openDrawerItem("Notes")
        waitForText("Old draft")
        rule.onNodeWithText("Old draft").performTouchInput { longClick() }
        rule.onNodeWithContentDescription("More").performClick()
        rule.onNodeWithText("Delete").performClick()
        openDrawerItem("Trash")
        waitForText("Old draft")
        rule.onAllNodesWithText("Empty trash").onFirst().performClick()
        waitForText("Empty trash?")
        rule.onAllNodesWithText("Empty trash").onLast().performClick()
        waitForText("No notes in trash")
        assertEquals(1, runBlocking { container.repository.getAll().size })
    }

    @Test
    fun t07_colors_layout_lightTheme() {
        val palette = listOf("Coral", "Peach", "Sand", "Mint", "Sage", "Fog", "Storm", "Dusk")
        seed(*palette.mapIndexed { i, name ->
            Note(title = "$name note", content = "Color #${i + 1}. " + "Some text. ".repeat(i + 1), color = i + 1)
        }.toTypedArray())
        seed(
            Note(
                title = "Packing list",
                isChecklist = true,
                checklist = ChecklistCodec.encode(
                    listOf(ChecklistItem(1, "Passport", true), ChecklistItem(2, "Charger"), ChecklistItem(3, "Sunscreen")),
                ),
                isPinned = true,
            ),
        )
        waitForText("Dusk note")
        shot("07a_grid_dark_colors")
        rule.onNodeWithContentDescription("List view").performClick()
        shot("07b_list_dark")
        rule.onNodeWithContentDescription("Grid view").performClick()
        container.settings.update { it.copy(themeMode = ThemeMode.LIGHT) }
        shot("07c_grid_light_colors")
        rule.onNodeWithText("Mint note").performClick()
        rule.onNodeWithContentDescription("Color").performClick()
        shot("07d_editor_light_palette")
        back()
    }

    @Test
    fun t08_editorSurvivesRotation_andEmptyNoteDiscarded() {
        rule.onNodeWithText("Note", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("editor-content").performTextInput("Typed before rotation")
        rule.activityRule.scenario.recreate()
        waitForText("Typed before rotation")
        back()
        waitForText("Typed before rotation")

        // A note opened and left empty must not be saved.
        rule.onNodeWithText("Note", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        back()
        rule.waitForIdle()
        pauseTyping()
        assertEquals(1, runBlocking { container.repository.getAll().size })
    }

    @Test
    fun t09_settingsScreen() {
        openDrawerItem("Settings")
        waitForText("Export notes")
        shot("09a_settings")
    }

    @Test
    fun t10_performance_1000Notes() {
        val seedMs = measureTimeMillis {
            runBlocking {
                repeat(1000) { i ->
                    container.repository.save(
                        Note(title = "Note #$i", content = "Body text for note number $i. " + "Lorem ipsum ".repeat(i % 20), color = i % 12),
                        emptyList(),
                    )
                }
            }
        }
        val renderMs = measureTimeMillis { waitForText("Note #999", timeout = 20_000) }
        shot("10a_1000_notes")
        val searchMs = measureTimeMillis {
            rule.onNode(hasSetTextAction()).performTextInput("number 512.")
            waitGone("Note #999", timeout = 20_000)
        }
        shot("10b_search_1000")
        rule.onNodeWithContentDescription("Clear search").performClick()
        waitForText("Note #999", timeout = 20_000)
        val scrollMs = measureTimeMillis {
            rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToIndex(900)
            rule.waitForIdle()
        }
        Log.i("QA", "PERF seed1000=${seedMs}ms render=${renderMs}ms search=${searchMs}ms scroll900=${scrollMs}ms")
    }

    @Test
    fun t11_backup_roundTrip_1000Notes() = runBlocking {
        val repo = container.repository
        val work = repo.createLabel("Work")!!
        repo.inTransaction {
            repeat(1000) { i ->
                val note = if (i % 3 == 0) {
                    Note(
                        title = "List $i", isChecklist = true,
                        checklist = ChecklistCodec.encode(listOf(ChecklistItem(1, "a", true), ChecklistItem(2, "b"))),
                    )
                } else {
                    Note(title = "Note $i", content = "Body $i ✓ ünïcödé", color = i % 12, isArchived = i % 7 == 0)
                }
                repo.save(note, if (i % 2 == 0) listOf(work) else emptyList())
            }
        }
        val before = repo.getAll().sortedBy { it.note.title }

        val bytes = java.io.ByteArrayOutputStream()
        val exportMs = measureTimeMillis { container.backup.export(bytes) }
        repo.deleteForever(before.map { it.note.id })
        repo.deleteLabel(work)

        val importMs = measureTimeMillis {
            container.backup.import(java.io.ByteArrayInputStream(bytes.toByteArray()))
        }
        val after = repo.getAll().sortedBy { it.note.title }
        Log.i("QA", "PERF export1000=${exportMs}ms import1000=${importMs}ms size=${bytes.size() / 1024}KB")

        assertEquals(before.size, after.size)
        before.zip(after).forEach { (b, a) ->
            assertEquals(b.note.copy(id = 0), a.note.copy(id = 0))
            assertEquals(b.labels.map { it.name }, a.labels.map { it.name })
        }
        assertTrue("import of 1000 notes should take < 5s, took ${importMs}ms", importMs < 5_000)
    }

    // --- New features ---

    /**
     * A real pause in typing. The test clock only runs frames when synced, so sync first
     * (the app sees the edit), wait out the undo debounce, then sync again.
     */
    private fun pauseTyping() {
        rule.waitForIdle()
        Thread.sleep(800)
        rule.waitForIdle()
    }

    private fun assertContent(expected: String) {
        // Compare characters only: the field's text carries formatting styles.
        rule.onNodeWithTag("editor-content").assert(
            SemanticsMatcher("EditableText is '$expected'") {
                it.config.getOrNull(SemanticsProperties.EditableText)?.text == expected
            },
        )
    }

    @Test
    fun t12_undoRedo_stepsThroughTypingBursts() {
        rule.onNodeWithText("Note", useUnmergedTree = true).performClick()
        rule.onNodeWithContentDescription("Undo").assertIsNotEnabled()
        rule.onNodeWithTag("editor-content").performTextInput("one")
        pauseTyping() // a pause in typing closes an undo step
        rule.onNodeWithTag("editor-content").performTextInput(" two")
        shot("12a_undo_enabled")

        rule.onNodeWithContentDescription("Undo").performClick()
        assertContent("one")
        rule.onNodeWithContentDescription("Redo").assertIsEnabled().performClick()
        assertContent("one two")
        rule.onNodeWithContentDescription("Redo").assertIsNotEnabled()

        rule.onNodeWithContentDescription("Undo").performClick()
        rule.onNodeWithContentDescription("Undo").performClick()
        assertContent("")
        rule.onNodeWithContentDescription("Undo").assertIsNotEnabled()

        // Typing after undo discards the redo branch.
        rule.onNodeWithContentDescription("Redo").performClick()
        rule.onNodeWithTag("editor-content").performTextInput("!")
        pauseTyping()
        rule.onNodeWithContentDescription("Redo").assertIsNotEnabled()
        assertContent("one!")
        back()
        rule.waitUntil(3_000) { runBlocking { container.repository.getAll() }.singleOrNull()?.note?.content == "one!" }
    }

    @Test
    fun t13_checklist_dragToReorder_andAccessibilityMove() {
        val items = listOf("Alpha", "Bravo", "Charlie", "Delta").mapIndexed { i, t -> ChecklistItem(i + 1L, t) }
        seed(Note(title = "Order test", isChecklist = true, checklist = ChecklistCodec.encode(items)))
        waitForText("Order test")
        rule.onNodeWithText("Order test").performClick()
        rule.waitUntil(3_000) { rule.onAllNodesWithContentDescription("Drag to reorder").fetchSemanticsNodes().size == 4 }

        val rows = rule.onAllNodesWithTag("checklist-item").fetchSemanticsNodes()
        val rowPitch = rows[1].boundsInRoot.top - rows[0].boundsInRoot.top
        val handle = rule.onAllNodesWithContentDescription("Drag to reorder")[0]
        handle.performTouchInput { down(center) }
        // Move in small steps with frames in between, the way a finger does.
        repeat(9) { // 2.25 rows: past Bravo and Charlie, short of Delta
            handle.performTouchInput { moveBy(Offset(0f, rowPitch * 0.25f)) }
            rule.waitForIdle()
        }
        shot("13a_dragging")
        rule.onAllNodesWithContentDescription("Drag to reorder")[2].performTouchInput { up() }
        rule.waitForIdle()

        fun order() = rule.onAllNodesWithTag("checklist-item").fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.EditableText].text }
        assertEquals(listOf("Bravo", "Charlie", "Alpha", "Delta"), order())
        shot("13b_after_drag")

        // Screen-reader path: "Move up" on Delta (last row).
        val deltaHandle = rule.onAllNodesWithContentDescription("Drag to reorder")[3].fetchSemanticsNode()
        rule.runOnUiThread {
            deltaHandle.config[SemanticsActions.CustomActions].first { it.label == "Move up" }.action()
        }
        rule.waitForIdle()
        assertEquals(listOf("Bravo", "Charlie", "Delta", "Alpha"), order())

        back()
        rule.waitUntil(3_000) {
            runBlocking { container.repository.getAll() }.single().note.items.map { it.text } ==
                listOf("Bravo", "Charlie", "Delta", "Alpha")
        }
    }

    @Test
    fun t14_swipe_rightArchives_leftTrashes_withUndo() {
        seed(Note(title = "Swipe to archive", content = "right"), Note(title = "Swipe to trash", content = "left", color = 2))
        waitForText("Swipe to trash")
        rule.onNodeWithContentDescription("List view").performClick() // full-width cards
        rule.waitForIdle()

        // Partial swipe to capture the revealed action, then finish it.
        val trashCard = rule.onNodeWithText("Swipe to trash")
        trashCard.performTouchInput { down(center) }
        repeat(4) { trashCard.performTouchInput { moveBy(Offset(-width * 0.06f, 0f)) }; rule.waitForIdle() }
        shot("14a_swipe_left_reveal")
        trashCard.performTouchInput { moveBy(Offset(-width * 0.5f, 0f)); up() }
        waitForText("Note moved to trash")
        waitGone("Swipe to trash")
        shot("14b_swiped_to_trash")
        rule.onNodeWithText("Undo").performClick()
        waitForText("Swipe to trash")

        rule.onNodeWithText("Swipe to archive").performTouchInput { swipeRight() }
        waitForText("Note archived")
        waitGone("Swipe to archive")

        val notes = runBlocking { container.repository.getAll() }.associateBy { it.note.title }
        assertTrue(notes.getValue("Swipe to archive").note.isArchived)
        assertTrue(!notes.getValue("Swipe to trash").note.isTrashed)

        // In the archive, swiping right unarchives.
        openDrawerItem("Archive")
        waitForText("Swipe to archive")
        rule.onNodeWithText("Swipe to archive").performTouchInput { swipeRight() }
        waitForText("Note unarchived")
        rule.waitUntil(3_000) { runBlocking { container.repository.getAll() }.none { it.note.isArchived } }
    }

    @Test
    fun t15_notificationsOff_warnsInEditorAndRemindersView() {
        val context = rule.activity
        org.junit.Assume.assumeFalse(
            "needs notifications disabled for this app",
            androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )
        val tomorrow = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
        seed(Note(title = "Pay rent", content = "Before the 1st", reminderAt = tomorrow))
        waitForText("Pay rent")
        rule.onNodeWithText("Pay rent").performClick()
        waitForText("this reminder won't alert you", substring = true)
        rule.onNodeWithText("Turn on").assertExists()
        shot("15a_editor_notifications_off")
        back()
        openDrawerItem("Reminders")
        waitForText("these reminders won't alert you", substring = true)
        shot("15b_reminders_notifications_off")
    }

    // --- v1.1 features ---

    @Test
    fun t16_images_attachShowOnCardViewAndCleanUp() {
        val id = seed(Note(title = "Holiday", content = "Photos from the beach")).single()
        val repo = container.repository
        val big = runBlocking { repo.addImage(id, testImage(width = 5000, height = 2500)) }
        runBlocking { repo.addImage(id, testImage(color = android.graphics.Color.rgb(40, 120, 230))) }
        // Large pictures are shrunk so they don't bloat storage.
        assertTrue("stored ${big.width}x${big.height}", maxOf(big.width, big.height) <= 2048)
        assertEquals(2f, big.width.toFloat() / big.height, 0.01f)

        waitForText("Holiday")
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Image").fetchSemanticsNodes().isNotEmpty() }
        // On the card, the picture comes after the title and text.
        fun top(node: androidx.compose.ui.test.SemanticsNodeInteraction) = node.fetchSemanticsNode().boundsInRoot.top
        val cardText = top(rule.onNodeWithText("Photos from the beach", useUnmergedTree = true))
        val cardImage = top(rule.onAllNodesWithContentDescription("Image", useUnmergedTree = true)[0])
        assertTrue("card image below text ($cardImage vs $cardText)", cardImage > cardText)
        shot("16a_card_with_image")

        rule.onNodeWithText("Holiday").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Image").fetchSemanticsNodes().size == 2 }
        // In the editor too: title, then body, then pictures.
        val bodyTop = top(rule.onNodeWithTag("editor-content"))
        val titleTop = top(rule.onNodeWithTag("editor-title"))
        val imageTop = top(rule.onAllNodesWithContentDescription("Image")[0])
        assertTrue("title above body", titleTop < bodyTop)
        assertTrue("pictures below body ($imageTop vs $bodyTop)", imageTop > bodyTop)
        shot("16b_editor_images")
        rule.onAllNodesWithContentDescription("Image")[0].performClick()
        rule.onNodeWithContentDescription("Delete image").assertExists()
        shot("16c_image_viewer")
        rule.onNodeWithContentDescription("Delete image").performClick()
        rule.onNodeWithText("Delete").performClick()
        rule.waitUntil(5_000) { runBlocking { repo.get(id) }!!.attachments.size == 1 }
        assertTrue("deleted image file removed", !container.repository.attachmentStore.file(big.fileName).exists())
        back()

        // Deleting the note for good removes its remaining image file too.
        val remaining = runBlocking { repo.get(id) }!!.attachments.single()
        runBlocking { repo.deleteForever(listOf(id)) }
        assertTrue(!container.repository.attachmentStore.file(remaining.fileName).exists())
    }

    @Test
    fun t17_imageOnlyNote_isKept_andCopiedWithDuplicate() = runBlocking {
        val repo = container.repository
        val id = repo.save(Note(), emptyList())
        repo.addImage(id, testImage())
        val copyId = repo.duplicate(id)!!
        val original = repo.get(id)!!.attachments.single()
        val copy = repo.get(copyId)!!.attachments.single()
        // The copy has its own file, so deleting one never breaks the other.
        assertTrue(original.fileName != copy.fileName)
        repo.deleteForever(listOf(id))
        assertTrue(repo.attachmentStore.file(copy.fileName).exists())

        // Opening and leaving a note that has only a picture must not throw it away as empty.
        rule.waitForIdle()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Image").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithContentDescription("Image")[0].performClick()
        rule.waitForIdle()
        back()
        pauseTyping()
        assertEquals(1, repo.getAll().size)
    }

    @Test
    fun t18_backupZip_roundTripsImagesAndRepeat() = runBlocking {
        val repo = container.repository
        val id = repo.save(Note(title = "With picture", reminderAt = System.currentTimeMillis() + 86_400_000, reminderRepeat = "WEEKLY"), emptyList())
        val image = repo.addImage(id, testImage())
        val originalBytes = repo.attachmentStore.file(image.fileName).readBytes()

        val zip = java.io.ByteArrayOutputStream()
        container.backup.export(zip)
        repo.deleteForever(listOf(id))

        assertEquals(1, container.backup.import(java.io.ByteArrayInputStream(zip.toByteArray())))
        val restored = repo.getAll().single()
        assertEquals("WEEKLY", restored.note.reminderRepeat)
        val restoredImage = restored.attachments.single()
        assertEquals(image.width, restoredImage.width)
        assertTrue(originalBytes.contentEquals(repo.attachmentStore.file(restoredImage.fileName).readBytes()))

        // A 1.0 JSON backup still imports.
        val legacy = """{"notes":[{"title":"From 1.0","content":"old format","labels":["Home"]}]}"""
        assertEquals(1, container.backup.import(java.io.ByteArrayInputStream(legacy.toByteArray())))
        assertEquals(listOf("Home"), repo.getAll().first { it.note.title == "From 1.0" }.labels.map { it.name })
    }

    @Test
    fun t19_repeatingReminder_movesToNextTimeWhenItFires() {
        val repo = container.repository
        val due = System.currentTimeMillis() - 1_000
        val id = seed(Note(title = "Water plants", reminderAt = due, reminderRepeat = "DAILY")).single()
        val fired = runBlocking { repo.onReminderFired(id) }
        assertEquals("Water plants", fired?.title)
        val next = runBlocking { repo.get(id) }!!.note
        assertEquals("DAILY", next.reminderRepeat)
        val expected = java.time.Instant.ofEpochMilli(due).atZone(java.time.ZoneId.systemDefault()).plusDays(1).toInstant().toEpochMilli()
        assertEquals(expected, next.reminderAt)

        waitForText("Water plants")
        rule.onNodeWithText("Water plants").performClick()
        waitForText("Daily", substring = true)
        rule.onNodeWithContentDescription("Reminder").performClick()
        waitForText("Repeat")
        shot("19a_reminder_dialog_repeat")
        rule.onNodeWithText("Cancel").performClick()
        back()
    }

    @Test
    fun t20_formatting_toolbarAndListContinuation() {
        rule.onNodeWithText("Note", useUnmergedTree = true).performClick()
        val body = rule.onNodeWithTag("editor-content")
        body.performTextInput("Groceries\n- milk")
        body.performTextInput("\n") // a single Enter, as from the keyboard
        assertContent("Groceries\n- milk\n- ") // Enter carried the bullet on
        body.performTextInput("eggs")
        // Select "eggs" and make it bold.
        body.performTextInputSelection(TextRange(19, 23))
        rule.onNodeWithContentDescription("Bold").performClick()
        assertContent("Groceries\n- milk\n- **eggs**")
        // Heading on the first line.
        body.performTextInputSelection(TextRange(2))
        rule.onNodeWithContentDescription("Heading").performClick()
        assertContent("# Groceries\n- milk\n- **eggs**")
        body.performTextInputSelection(TextRange(29))
        body.performTextInput("\n")
        body.performTextInput("\n") // Enter on an empty bullet ends the list
        body.performTextInput("See https://example.com")
        rule.waitForIdle()
        waitForText("example.com") // link chip
        shot("20a_formatting_editor")
        back()
        waitForText("Groceries", substring = true)
        shot("20b_formatting_card")
        val saved = runBlocking { container.repository.getAll() }.single().note.content
        assertEquals("# Groceries\n- milk\n- **eggs**\nSee https://example.com", saved)
    }

    @Test
    fun t21_appLock_coversAppUntilUnlocked() {
        seed(Note(title = "Secret plans"))
        waitForText("Secret plans")
        container.settings.update { it.copy(appLock = true) }
        container.appLock.lockNow()
        waitForText("Simple Note Pro is locked")
        rule.onNodeWithText("Unlock").assertExists()
        // Touches don't reach the notes underneath.
        rule.onNodeWithText("Secret plans").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Simple Note Pro is locked").assertExists()
        assertTrue(rule.onAllNodesWithTag("editor-content").fetchSemanticsNodes().isEmpty())

        container.appLock.unlock()
        waitGone("Simple Note Pro is locked")
        container.settings.update { it.copy(appLock = false) }
    }

    // --- v1.2 features ---

    /** A white "receipt" with printed lines of dark text, like a photo of a document. */
    private fun receiptImage(vararg lines: String): android.net.Uri {
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 72f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        android.graphics.Canvas(bitmap).apply { lines.forEachIndexed { i, line -> drawText(line, 80f, 200f + i * 140f, paint) } }
        val file = File(rule.activity.cacheDir, "receipt-${System.nanoTime()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return android.net.Uri.fromFile(file)
    }

    @Test
    fun t22_textInPhotos_isReadSearchableAndAddable() {
        val repo = container.repository
        val id = seed(Note(title = "Hardware store")).single()
        runBlocking {
            repo.addImage(id, receiptImage("INVOICE 4821", "Paint roller", "Total 36.50"))
            repo.readPendingImageText()
        }
        val text = runBlocking { repo.get(id) }!!.attachments.single().ocrText.orEmpty()
        Log.i("QA", "OCR read: ${text.replace('\n', '|')}")
        assertTrue("read the invoice number, got '$text'", "4821" in text)
        assertTrue("read a word, got '$text'", text.contains("roller", ignoreCase = true))

        // Search finds the note by a word that exists only inside the picture.
        waitForText("Hardware store")
        rule.onNode(hasSetTextAction()).performTextInput("roller")
        waitForText("Found in image")
        rule.onNodeWithText("Hardware store").assertExists()
        shot("22a_search_found_in_image")
        rule.onNodeWithContentDescription("Clear search").performClick()

        // The viewer shows the text and can add it to the note.
        rule.onNodeWithText("Hardware store").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Image").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithContentDescription("Image")[0].performClick()
        rule.onNodeWithContentDescription("Text in image").performClick()
        waitForText("INVOICE", substring = true)
        shot("22b_text_in_image_dialog")
        rule.onNodeWithText("Add to note").performClick()
        rule.waitForIdle()
        back()
        rule.waitUntil(5_000) { runBlocking { repo.get(id) }!!.note.content.contains("4821") }
    }

    @Test
    fun t23_pictureWithoutText_isMarkedReadNotRetried() = runBlocking {
        val repo = container.repository
        val id = repo.save(Note(title = "Plain picture"), emptyList())
        repo.addImage(id, testImage())
        assertEquals(1, repo.readPendingImageText())
        assertEquals("", repo.get(id)!!.attachments.single().ocrText)
        assertEquals("nothing left to read", 0, repo.readPendingImageText())
    }

    @Test
    fun t24_widgetAndTile_areRegistered_andNewNoteIntentOpensEditor() {
        val context = rule.activity
        val widgets = android.appwidget.AppWidgetManager.getInstance(context)
            .installedProviders.filter { it.provider.packageName == context.packageName }
        assertEquals(listOf("com.holymanzion.simplenotepro.widget.NotesWidgetReceiver"), widgets.map { it.provider.className })

        val tiles = context.packageManager.queryIntentServices(
            android.content.Intent(android.service.quicksettings.TileService.ACTION_QS_TILE).setPackage(context.packageName), 0,
        )
        assertEquals(listOf("com.holymanzion.simplenotepro.tile.NewNoteTileService"), tiles.map { it.serviceInfo.name })

        // Refreshing widgets must not crash, with or without notes / the lock.
        seed(Note(title = "Pinned idea", isPinned = true))
        runBlocking { com.holymanzion.simplenotepro.widget.NotesWidget().updateAll(context) }
        container.settings.update { it.copy(appLock = true) }
        runBlocking { com.holymanzion.simplenotepro.widget.NotesWidget().updateAll(context) }
        container.settings.update { it.copy(appLock = false) }

        // The tile and the widget's "+" send this intent; it must open a blank editor.
        waitForText("Pinned idea")
        context.startActivity(
            android.content.Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_NEW_NOTE)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("editor-content").fetchSemanticsNodes().isNotEmpty() }
        back()
    }
}
