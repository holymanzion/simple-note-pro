package com.holymanzion.simplenotepro

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import com.holymanzion.simplenotepro.text.RichText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextTest {

    private fun tfv(text: String, start: Int, end: Int = start) = TextFieldValue(text, TextRange(start, end))

    // --- toggleInline ---

    @Test fun boldWrapsSelection() {
        val r = RichText.toggleInline(tfv("say hello now", 4, 9), "**")
        assertEquals("say **hello** now", r.text)
        assertEquals(TextRange(6, 11), r.selection) // still selects "hello"
    }

    @Test fun boldTwiceUnwraps() {
        val once = RichText.toggleInline(tfv("say hello now", 4, 9), "**")
        val twice = RichText.toggleInline(once, "**")
        assertEquals("say hello now", twice.text)
        assertEquals(TextRange(4, 9), twice.selection)
    }

    @Test fun collapsedCursorInsertsPairAndSitsBetween() {
        val r = RichText.toggleInline(tfv("ab", 1), "~~")
        assertEquals("a~~~~b", r.text)
        assertEquals(TextRange(3), r.selection)
    }

    // --- toggleLinePrefix ---

    @Test fun bulletAddsToEverySelectedLine() {
        val r = RichText.toggleLinePrefix(tfv("one\ntwo\nthree", 1, 6), "- ")
        assertEquals("- one\n- two\nthree", r.text)
    }

    @Test fun bulletRemovesWhenAllLinesHaveIt() {
        val r = RichText.toggleLinePrefix(tfv("- one\n- two", 0, 9), "- ")
        assertEquals("one\ntwo", r.text)
    }

    @Test fun headingReplacesListMarkerInsteadOfStacking() {
        val r = RichText.toggleLinePrefix(tfv("- item", 3), "# ")
        assertEquals("# item", r.text)
    }

    @Test fun prefixOnLaterLineKeepsCursorOnThatLine() {
        val r = RichText.toggleLinePrefix(tfv("a\nbc", 3), "- ")
        assertEquals("a\n- bc", r.text)
        assertEquals(TextRange(5), r.selection)
    }

    // --- continueList ---

    @Test fun enterAfterBulletStartsNewBullet() {
        val old = tfv("- milk", 6)
        val r = RichText.continueList(old, tfv("- milk\n", 7))!!
        assertEquals("- milk\n- ", r.text)
        assertEquals(TextRange(9), r.selection)
    }

    @Test fun enterAfterNumberedItemIncrements() {
        val r = RichText.continueList(tfv("9. x", 4), tfv("9. x\n", 5))!!
        assertEquals("9. x\n10. ", r.text)
    }

    @Test fun enterOnEmptyBulletEndsList() {
        val r = RichText.continueList(tfv("- a\n- ", 6), tfv("- a\n- \n", 7))!!
        assertEquals("- a\n", r.text)
        assertEquals(TextRange(4), r.selection)
    }

    @Test fun ordinaryTypingIsLeftAlone() {
        assertNull(RichText.continueList(tfv("- a", 3), tfv("- ab", 4)))
        assertNull(RichText.continueList(tfv("plain", 5), tfv("plain\n", 6)))
    }

    @Test fun pasteContainingNewlineIsNotTreatedAsEnter() {
        assertNull(RichText.continueList(tfv("- a", 3), tfv("- ax\ny", 6)))
    }

    // --- rendering ---

    @Test fun renderStripsMarksAndStyles() {
        val out = RichText.render("# Title\nsome **bold** and *it* text\n- item", Color.Blue)
        assertEquals("Title\nsome bold and it text\n•  item", out.text)
        val bold = out.spanStyles.first { it.item.fontWeight == FontWeight.Bold && out.text.substring(it.start, it.end) == "bold" }
        assertEquals("bold", out.text.substring(bold.start, bold.end))
        assertTrue(out.spanStyles.any { it.item.fontStyle == FontStyle.Italic && out.text.substring(it.start, it.end) == "it" })
    }

    @Test fun editingStyleKeepsTextIdentical() {
        val text = "# H\n**b** *i* ~~s~~ https://example.com/x - not a list\n- list\n1. num"
        assertEquals(text, RichText.styleForEditing(text, Color.Gray, Color.Blue).text)
    }

    @Test fun asterisksInsideWordsAreNotItalic() {
        val out = RichText.render("2*3*4 and snake_case", Color.Blue)
        assertEquals("2*3*4 and snake_case", out.text)
    }

    @Test fun linksAreFoundWithoutTrailingPunctuation() {
        assertEquals(
            listOf("https://example.com/a?b=1", "http://x.org"),
            RichText.links("See https://example.com/a?b=1. Also (http://x.org), again https://example.com/a?b=1"),
        )
    }
}
