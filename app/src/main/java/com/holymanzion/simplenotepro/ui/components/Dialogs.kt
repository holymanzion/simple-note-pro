package com.holymanzion.simplenotepro.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FormatColorReset
import androidx.compose.material.icons.outlined.NextWeek
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.holymanzion.simplenotepro.data.Label
import com.holymanzion.simplenotepro.data.Repeat
import com.holymanzion.simplenotepro.ui.theme.LocalDarkTheme
import com.holymanzion.simplenotepro.ui.theme.NoteColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

@Composable
fun ColorPickerRow(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val dark = LocalDarkTheme.current
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(NoteColors) { index, noteColor ->
            val fill = (if (dark) noteColor.dark else noteColor.light).takeIf { it != Color.Unspecified }
                ?: MaterialTheme.colorScheme.surface
            val isSelected = index == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .border(
                        BorderStroke(
                            if (isSelected) 2.5.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        CircleShape,
                    )
                    .padding(3.dp)
                    .background(fill, CircleShape)
                    .clickable { onSelect(index) },
            ) {
                when {
                    isSelected -> Icon(Icons.Outlined.Check, contentDescription = "${noteColor.name}, selected")
                    index == 0 -> Icon(
                        Icons.Outlined.FormatColorReset,
                        contentDescription = "No color",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun ColorPickerDialog(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note color") },
        text = { ColorPickerRow(selected, onSelect = { onSelect(it); onDismiss() }) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun RepeatPicker(repeat: Repeat?, onChange: (Repeat?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListItem(
            headlineContent = { Text("Repeat") },
            leadingContent = { Icon(Icons.Outlined.Repeat, contentDescription = null) },
            trailingContent = {
                Text(repeat?.label ?: "Doesn't repeat", color = MaterialTheme.colorScheme.primary)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (listOf<Repeat?>(null) + Repeat.entries).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option?.label ?: "Doesn't repeat") },
                    leadingIcon = if (option == repeat) { { Icon(Icons.Outlined.Check, contentDescription = null) } } else null,
                    onClick = { onChange(option); open = false },
                )
            }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
}

private fun millisOf(date: LocalDate, time: LocalTime): Long =
    LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(
    current: Long?,
    currentRepeat: Repeat?,
    onSet: (at: Long, repeat: Repeat?) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(0) } // 0 = presets, 1 = date, 2 = time
    var repeat by remember { mutableStateOf(currentRepeat) }
    var pickedDate by remember { mutableStateOf(LocalDate.now()) }
    val now = LocalDateTime.now()
    val today = now.toLocalDate()

    when (step) {
        0 -> {
            val laterToday = if (now.hour < 17) millisOf(today, LocalTime.of(18, 0)) else null
            val tomorrow = millisOf(today.plusDays(1), LocalTime.of(8, 0))
            val nextWeek = millisOf(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)), LocalTime.of(8, 0))
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(if (current == null) "Add reminder" else "Edit reminder") },
                text = {
                    Column {
                        if (current != null) {
                            Text(
                                "Currently: ${reminderLabel(current, currentRepeat)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        RepeatPicker(repeat, onChange = { repeat = it })
                        if (laterToday != null) {
                            PresetRow(Icons.Outlined.WbTwilight, "Later today", "6:00 PM") { onSet(laterToday, repeat); onDismiss() }
                        }
                        PresetRow(Icons.Outlined.WbSunny, "Tomorrow morning", "8:00 AM") { onSet(tomorrow, repeat); onDismiss() }
                        PresetRow(Icons.Outlined.NextWeek, "Next week", "Mon, 8:00 AM") { onSet(nextWeek, repeat); onDismiss() }
                        PresetRow(Icons.Outlined.CalendarMonth, "Pick date & time", null) { step = 1 }
                    }
                },
                // With a reminder already set, Save keeps its time and applies the repeat choice.
                confirmButton = {
                    if (current != null && repeat != currentRepeat) {
                        TextButton(onClick = { onSet(current, repeat); onDismiss() }) { Text("Save") }
                    } else {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                    }
                },
                dismissButton = if (current != null) {
                    { TextButton(onClick = { onRemove(); onDismiss() }) { Text("Remove") } }
                } else null,
            )
        }

        1 -> {
            val initialUtc = (current?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } ?: today)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val todayUtc = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val dateState = rememberDatePickerState(
                initialSelectedDateMillis = maxOf(initialUtc, todayUtc),
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
                },
            )
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(
                        enabled = dateState.selectedDateMillis != null,
                        onClick = {
                            dateState.selectedDateMillis?.let {
                                pickedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                                step = 2
                            }
                        },
                    ) { Text("Next") }
                },
                dismissButton = { TextButton(onClick = { step = 0 }) { Text("Back") } },
            ) {
                DatePicker(state = dateState)
            }
        }

        else -> {
            val initial = current?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() }
                ?: now.toLocalTime().plusHours(1).withMinute(0)
            val timeState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
            val chosen = millisOf(pickedDate, LocalTime.of(timeState.hour, timeState.minute))
            val inPast = chosen <= System.currentTimeMillis()
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Pick a time") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TimePicker(state = timeState)
                        if (inPast) {
                            Text(
                                "Choose a time in the future",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(enabled = !inPast, onClick = { onSet(chosen, repeat); onDismiss() }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { step = 1 }) { Text("Back") } },
            )
        }
    }
}

@Composable
private fun PresetRow(icon: ImageVector, title: String, trailing: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = trailing?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun LabelPickerDialog(
    labels: List<Label>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    val exists = labels.any { it.name.equals(newName.trim(), ignoreCase = true) }
    fun create() {
        if (newName.isNotBlank() && !exists) onCreate(newName.trim())
        newName = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Labels") },
        text = {
            Column {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(50) },
                    placeholder = { Text("Create new label") },
                    singleLine = true,
                    isError = exists,
                    supportingText = if (exists) { { Text("Label already exists") } } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                    trailingIcon = {
                        IconButton(onClick = { create() }, enabled = newName.isNotBlank() && !exists) {
                            Icon(Icons.Outlined.Add, contentDescription = "Create label")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.padding(4.dp))
                if (labels.isEmpty()) {
                    Text(
                        "No labels yet. Labels help you group related notes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(labels, key = { it.id }) { label ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggle(label.id) }
                                    .padding(vertical = 2.dp),
                            ) {
                                Checkbox(checked = label.id in selected, onCheckedChange = { onToggle(label.id) })
                                Spacer(Modifier.width(4.dp))
                                Text(label.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = if (destructive) { { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) } } else null,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SectionDivider() = HorizontalDivider(Modifier.padding(vertical = 8.dp))
