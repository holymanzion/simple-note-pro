package com.holymanzion.simplenotepro.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em

/**
 * Lightweight formatting stored as plain Markdown-style text, so notes stay readable
 * anywhere they're shared or exported:
 *
 *   **bold**   *italic*   ~~strikethrough~~   # Heading   ## Subheading   - bullet   1. numbered
 *
 * Links (http/https) are detected automatically.
 */
object RichText {
    private val INLINE = Regex(
        """\*\*(?<bold>[^*\n]+?)\*\*""" +
            """|~~(?<strike>[^~\n]+?)~~""" +
            """|(?<![\w*])\*(?<italic>[^*\s](?:[^*\n]*?[^*\s])?)\*(?![\w*])""" +
            """|(?<url>https?://[^\s<>()]+[^\s<>().,;:!?'"])""",
    )
    private val HEADING = Regex("""^(#{1,2}) """)
    private val BULLET = Regex("""^(\s*)([-*•]) """)
    private val NUMBERED = Regex("""^(\s*)(\d{1,3})\. """)

    private val BOLD = SpanStyle(fontWeight = FontWeight.Bold)
    private val ITALIC = SpanStyle(fontStyle = FontStyle.Italic)
    private val STRIKE = SpanStyle(textDecoration = TextDecoration.LineThrough)
    private val H1 = SpanStyle(fontSize = 1.35.em, fontWeight = FontWeight.Bold)
    private val H2 = SpanStyle(fontSize = 1.15.em, fontWeight = FontWeight.SemiBold)

    /**
     * Styled text for display (note cards): marks are removed, bullets become "•".
     */
    fun render(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
        text.split('\n').forEachIndexed { index, rawLine ->
            if (index > 0) append('\n')
            var line = rawLine
            var lineStyle: SpanStyle? = null
            HEADING.find(line)?.let { match ->
                lineStyle = if (match.groupValues[1].length == 1) H1 else H2
                line = line.substring(match.range.last + 1)
            }
            BULLET.find(line)?.let { match ->
                line = match.groupValues[1] + "•  " + line.substring(match.range.last + 1)
            }
            val start = length
            appendInline(line, linkColor, keepMarks = false, markColor = Color.Unspecified)
            lineStyle?.let { addStyle(it, start, length) }
        }
    }

    /**
     * Styled text for the editor. The text is unchanged, character for character, so the
     * cursor maps 1:1; marks stay visible but faded.
     */
    fun styleForEditing(text: String, markColor: Color, linkColor: Color): AnnotatedString = buildAnnotatedString {
        text.split('\n').forEachIndexed { index, line ->
            if (index > 0) append('\n')
            val start = length
            var rest = line
            var lineStyle: SpanStyle? = null
            HEADING.find(line)?.let { match ->
                lineStyle = if (match.groupValues[1].length == 1) H1 else H2
                withStyle(SpanStyle(color = markColor)) { append(match.value) }
                rest = line.substring(match.value.length)
            } ?: (BULLET.find(line) ?: NUMBERED.find(line))?.let { match ->
                withStyle(SpanStyle(color = markColor, fontWeight = FontWeight.Bold)) { append(match.value) }
                rest = line.substring(match.value.length)
            }
            appendInline(rest, linkColor, keepMarks = true, markColor = markColor)
            lineStyle?.let { addStyle(it, start, length) }
        }
    }

    /** Every link in [text], in order, without duplicates. */
    fun links(text: String): List<String> =
        INLINE.findAll(text).mapNotNull { it.groups["url"]?.value }.distinct().toList()

    private fun AnnotatedString.Builder.appendInline(line: String, linkColor: Color, keepMarks: Boolean, markColor: Color) {
        var cursor = 0
        for (match in INLINE.findAll(line)) {
            append(line.substring(cursor, match.range.first))
            val groups = match.groups
            val (inner, style, mark) = when {
                groups["bold"] != null -> Triple(groups["bold"]!!.value, BOLD, "**")
                groups["strike"] != null -> Triple(groups["strike"]!!.value, STRIKE, "~~")
                groups["italic"] != null -> Triple(groups["italic"]!!.value, ITALIC, "*")
                else -> Triple(groups["url"]!!.value, SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), "")
            }
            if (keepMarks && mark.isNotEmpty()) withStyle(SpanStyle(color = markColor)) { append(mark) }
            withStyle(style) { append(inner) }
            if (keepMarks && mark.isNotEmpty()) withStyle(SpanStyle(color = markColor)) { append(mark) }
            cursor = match.range.last + 1
        }
        append(line.substring(cursor))
    }

    // --- Editing actions (toolbar buttons and Enter) ---

    /**
     * Wraps the selection in [mark] (e.g. "**"), or unwraps it if it's already wrapped.
     * With nothing selected, inserts a pair of marks and puts the cursor between them.
     */
    fun toggleInline(value: TextFieldValue, mark: String): TextFieldValue {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max
        val wrapped = start >= mark.length && end + mark.length <= text.length &&
            text.substring(start - mark.length, start) == mark && text.substring(end, end + mark.length) == mark
        return when {
            wrapped -> TextFieldValue(
                text.removeRange(end, end + mark.length).removeRange(start - mark.length, start),
                TextRange(start - mark.length, end - mark.length),
            )
            start == end -> TextFieldValue(
                text.substring(0, start) + mark + mark + text.substring(start),
                TextRange(start + mark.length),
            )
            else -> TextFieldValue(
                text.substring(0, start) + mark + text.substring(start, end) + mark + text.substring(end),
                TextRange(start + mark.length, end + mark.length),
            )
        }
    }

    /**
     * Adds [prefix] (e.g. "- " or "# ") to every line the selection touches, or removes it
     * if all of them already have it. Switching between heading levels or list types
     * replaces the old prefix instead of stacking them.
     */
    fun toggleLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
        val text = value.text
        val firstLine = text.lastIndexOf('\n', value.selection.min - 1) + 1
        val lastLineEnd = text.indexOf('\n', value.selection.max).let { if (it < 0) text.length else it }
        val lines = text.substring(firstLine, lastLineEnd).split('\n')
        val removing = lines.all { it.startsWith(prefix) }
        var delta = 0
        var firstDelta = 0
        val newLines = lines.mapIndexed { i, line ->
            val existing = LINE_PREFIX.find(line)?.value.orEmpty()
            val updated = if (removing) line.removePrefix(prefix) else prefix + line.removePrefix(existing)
            if (i == 0) firstDelta = updated.length - line.length
            delta += updated.length - line.length
            updated
        }
        val newText = text.substring(0, firstLine) + newLines.joinToString("\n") + text.substring(lastLineEnd)
        val selStart = (value.selection.min + firstDelta).coerceIn(firstLine, newText.length)
        val selEnd = (value.selection.max + delta).coerceIn(selStart, newText.length)
        return TextFieldValue(newText, TextRange(selStart, selEnd))
    }

    private val LINE_PREFIX = Regex("""^(#{1,2} |[-*•] |\d{1,3}\. )""")

    /**
     * Continues a list when Enter is pressed at the end of a list item, and ends the list
     * when Enter is pressed on an empty item. Returns null when [new] isn't that case.
     */
    fun continueList(old: TextFieldValue, new: TextFieldValue): TextFieldValue? {
        val cursor = new.selection.start
        val insertedEnter = new.selection.collapsed && old.selection.collapsed &&
            new.text.length == old.text.length + 1 && cursor > 0 && new.text[cursor - 1] == '\n' &&
            new.text.removeRange(cursor - 1, cursor) == old.text
        if (!insertedEnter) return null
        val lineStart = new.text.lastIndexOf('\n', cursor - 2) + 1
        val previousLine = new.text.substring(lineStart, cursor - 1)
        val bullet = BULLET.find(previousLine)
        val numbered = NUMBERED.find(previousLine)
        val marker = bullet ?: numbered ?: return null
        if (previousLine.length == marker.value.length) {
            // Enter on an empty item: drop the marker and the new line, ending the list.
            val text = new.text.removeRange(lineStart, cursor)
            return TextFieldValue(text, TextRange(lineStart))
        }
        val next = if (numbered != null) {
            "${numbered.groupValues[1]}${numbered.groupValues[2].toInt() + 1}. "
        } else {
            bullet!!.value
        }
        return TextFieldValue(new.text.substring(0, cursor) + next + new.text.substring(cursor), TextRange(cursor + next.length))
    }
}
