package com.holymanzion.simplenotepro.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.holymanzion.simplenotepro.data.BuiltInTemplates
import com.holymanzion.simplenotepro.data.NoteTemplate

/** Pick a template for a new note. The user's own come first and can be deleted. */
@Composable
internal fun TemplatePickerDialog(
    mine: List<NoteTemplate>,
    onPick: (NoteTemplate) -> Unit,
    onDelete: (NoteTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    var deleting by remember { mutableStateOf<NoteTemplate?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New from template") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (mine.isNotEmpty()) {
                    SectionTitle("Your templates")
                    mine.forEach { template ->
                        TemplateRow(template, onClick = { onPick(template) }, onDelete = { deleting = template })
                    }
                    SectionTitle("Built in")
                } else {
                    Text(
                        "Tip: save any note as a template from its ⋮ menu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                BuiltInTemplates.all.forEach { template ->
                    TemplateRow(template, onClick = { onPick(template) }, onDelete = null)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    deleting?.let { template ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete template?") },
            text = { Text("\"${template.name}\" will be removed. Notes made from it are kept.") },
            confirmButton = {
                TextButton(onClick = { onDelete(template); deleting = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun TemplateRow(template: NoteTemplate, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(template.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = template.summary().takeIf { it.isNotBlank() }?.let { summary ->
            { Text(summary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = {
            Icon(if (template.isChecklist) Icons.Outlined.CheckBox else Icons.AutoMirrored.Outlined.Notes, contentDescription = null)
        },
        trailingContent = onDelete?.let { delete ->
            { IconButton(onClick = delete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete template ${template.name}") } }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("template-${template.name}"),
    )
}
