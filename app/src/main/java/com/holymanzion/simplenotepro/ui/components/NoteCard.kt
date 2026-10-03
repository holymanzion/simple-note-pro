package com.holymanzion.simplenotepro.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.holymanzion.simplenotepro.data.Attachment
import com.holymanzion.simplenotepro.data.AttachmentStore
import com.holymanzion.simplenotepro.data.NoteWithLabels
import com.holymanzion.simplenotepro.text.RichText
import com.holymanzion.simplenotepro.ui.theme.noteBackground

private const val PREVIEW_ITEMS = 8

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NoteCard(
    item: NoteWithLabels,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown when a search found this note only through text inside its pictures. */
    foundInImage: Boolean = false,
) {
    val note = item.note
    val linkColor = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    val background = noteBackground(note.color, MaterialTheme.colorScheme.surface)
    val border = when {
        selected -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
        note.color == 0 -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        else -> null
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        color = background,
        border = border,
    ) {
        Column {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (note.title.isNotBlank() || note.isPinned) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (note.isPinned) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val hasBody = if (note.isChecklist) note.items.isNotEmpty() else note.content.isNotBlank()
                if (hasBody && note.title.isNotBlank()) Spacer(Modifier.padding(top = 6.dp))

                if (note.isChecklist) {
                    val items = note.items
                    items.take(PREVIEW_ITEMS).forEach { checklistItem ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                            Icon(
                                if (checklistItem.checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                checklistItem.text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textDecoration = if (checklistItem.checked) TextDecoration.LineThrough else null,
                                color = if (checklistItem.checked) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (items.size > PREVIEW_ITEMS) {
                        Text(
                            "+ ${items.size - PREVIEW_ITEMS} more",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                } else if (note.content.isNotBlank()) {
                    Text(
                        remember(note.content, linkColor) { RichText.render(note.content, linkColor) },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!hasBody && note.title.isBlank() && item.attachments.isEmpty()) {
                    Text(
                        "Empty note",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Pictures come after the writing, as in the editor.
                if (item.attachments.isNotEmpty()) {
                    val hasText = hasBody || note.title.isNotBlank() || note.isPinned
                    CardImages(item.attachments, Modifier.padding(top = if (hasText) 10.dp else 0.dp))
                }

                val reminder = note.reminderAt
                if (reminder != null || item.labels.isNotEmpty() || foundInImage) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        if (reminder != null) {
                            MiniChip(
                                Icons.Outlined.Alarm,
                                reminderLabel(reminder, note.repeat),
                                strike = reminder < System.currentTimeMillis(),
                            )
                        }
                        if (foundInImage) MiniChip(Icons.Outlined.ImageSearch, "Found in image")
                        item.labels.take(3).forEach { MiniChip(Icons.Outlined.Label, it.name) }
                        if (item.labels.size > 3) MiniChip(null, "+${item.labels.size - 3}")
                    }
                }
            }
        }
    }
}

/** The first image below the card's text, with a count when there are more. */
@Composable
private fun CardImages(attachments: List<Attachment>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val first = attachments.first()
    // Portrait photos (3:4) show whole; only very tall screenshots or panoramas are trimmed,
    // so one picture can't dominate the grid.
    val ratio = if (first.width > 0 && first.height > 0) (first.width.toFloat() / first.height).coerceIn(0.56f, 2.2f) else 4f / 3f
    Box(modifier.clip(RoundedCornerShape(10.dp))) {
        AsyncImage(
            model = AttachmentStore.file(context, first.fileName),
            contentDescription = "Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio),
        )
        if (attachments.size > 1) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${attachments.size}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun MiniChip(icon: ImageVector?, text: String, strike: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        modifier = if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (strike) TextDecoration.LineThrough else null,
            )
        }
    }
}
