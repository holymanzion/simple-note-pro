package com.holymanzion.simplenotepro

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.holymanzion.simplenotepro.data.NoteTemplate
import com.holymanzion.simplenotepro.text.RichText
import com.holymanzion.simplenotepro.ui.TwoPane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NewFeaturesTest {

    private fun tfv(text: String, start: Int, end: Int = start) = TextFieldValue(text, TextRange(start, end))

    // --- Voice typing ---

    @Test fun spokenIntoEmptyNoteIsCapitalised() {
        val r = RichText.insertSpoken(tfv("", 0), "buy some milk")
        assertEquals("Buy some milk", r.text)
        assertEquals(TextRange(13), r.selection)
    }

    @Test fun spokenAfterWordGetsASpace() {
        val r = RichText.insertSpoken(tfv("Call", 4), "mum tonight")
        assertEquals("Call mum tonight", r.text)
        assertEquals(TextRange(16), r.selection)
    }

    @Test fun spokenAfterFullStopStartsASentence() {
        assertEquals("Done. Next step", RichText.insertSpoken(tfv("Done.", 5), "next step").text)
    }

    @Test fun spokenInMiddleIsSpacedOnBothSides() {
        val r = RichText.insertSpoken(tfv("one three", 3), "two")
        assertEquals("one two three", r.text)
        assertEquals(TextRange(7), r.selection)
    }

    @Test fun spokenBeforePunctuationHasNoTrailingSpace() {
        assertEquals("Hello world.", RichText.insertSpoken(tfv("Hello.", 5), "world").text)
    }

    @Test fun spokenAfterBulletIsCapitalised() {
        assertEquals("- Eggs", RichText.insertSpoken(tfv("- ", 2), "eggs").text)
    }

    @Test fun spokenReplacesSelection() {
        assertEquals("Meet on Friday", RichText.insertSpoken(tfv("Meet on Monday", 8, 14), "Friday").text)
    }

    @Test fun blankSpeechChangesNothing() {
        val value = tfv("Keep", 4)
        assertEquals(value, RichText.insertSpoken(value, "   "))
    }

    // --- Templates ---

    @Test fun templateDatePlaceholderIsFilledIn() {
        val t = NoteTemplate(name = "Journal", title = "Day {date}", content = "On {date}:").filled(LocalDate.of(2026, 11, 17))
        assertFalse(t.title.contains("{date}"))
        assertTrue(t.title, t.title.contains("2026"))
        assertTrue(t.content.startsWith("On ") && !t.content.contains("{date}"))
    }

    @Test fun templateSummaryDropsFormattingMarks() {
        val t = NoteTemplate(name = "Meeting", content = "**Attendees:** \n\n## Agenda\n- ")
        assertEquals("Attendees: · Agenda", t.summary())
    }

    @Test fun savedTemplatesArentBuiltIn() {
        assertFalse(NoteTemplate(id = 3, name = "Mine").isBuiltIn)
        assertTrue(NoteTemplate(id = -1, name = "Shopping").isBuiltIn)
    }

    // --- Tablet layout ---

    @Test fun twoPanesOnlyOnLargeScreens() {
        assertFalse("phone, upright", TwoPane.isWide(411, 891))
        assertFalse("phone, sideways", TwoPane.isWide(891, 411))
        assertFalse("small tablet, upright", TwoPane.isWide(600, 960))
        assertTrue("tablet, landscape", TwoPane.isWide(1280, 800))
        assertTrue("large tablet, upright", TwoPane.isWide(800, 1280))
    }
}
