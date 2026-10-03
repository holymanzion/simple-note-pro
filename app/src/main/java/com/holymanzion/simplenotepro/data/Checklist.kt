package com.holymanzion.simplenotepro.data

import org.json.JSONArray
import org.json.JSONObject

data class ChecklistItem(
    val id: Long,
    val text: String,
    val checked: Boolean = false,
)

object ChecklistCodec {
    private var lastId = 0L

    /** Monotonic id that stays unique even when called many times in the same millisecond. */
    @Synchronized
    fun newId(): Long {
        lastId = maxOf(lastId + 1, System.currentTimeMillis())
        return lastId
    }

    fun encode(items: List<ChecklistItem>): String = JSONArray().apply {
        items.forEach { put(JSONObject().put("id", it.id).put("t", it.text).put("c", it.checked)) }
    }.toString()

    fun decode(raw: String): List<ChecklistItem> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ChecklistItem(o.optLong("id", i.toLong()), o.optString("t"), o.optBoolean("c"))
            }
        }.getOrDefault(emptyList())
    }

    private val checkboxPrefix = Regex("""^\s*(?:[-*]\s*)?(\[[ xX]]|☐|☑)\s*""")

    /** Splits plain text into checklist items, recognising "[x]" / "☑" style prefixes. */
    fun fromText(text: String): List<ChecklistItem> =
        text.lines().filter { it.isNotBlank() }.map { line ->
            val match = checkboxPrefix.find(line)
            val checked = match != null && (match.value.contains('x', ignoreCase = true) || match.value.contains('☑'))
            ChecklistItem(newId(), if (match != null) line.substring(match.range.last + 1) else line.trim(), checked)
        }

    fun toText(items: List<ChecklistItem>): String = items.joinToString("\n") { it.text }
}
