package com.holymanzion.simplenotepro.ui.editor

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.holymanzion.simplenotepro.data.Attachment
import com.holymanzion.simplenotepro.data.AttachmentStore
import com.holymanzion.simplenotepro.text.RichText
import java.io.File

internal const val MAX_IMAGES_PER_PICK = 10

/** Width / height, kept within [min]..[max] so extreme shapes stay usable on screen. */
internal fun Attachment.aspectRatio(min: Float, max: Float): Float =
    if (width > 0 && height > 0) (width.toFloat() / height).coerceIn(min, max) else 4f / 3f

/** The note body, with formatting marks styled as you type. */
@Composable
internal fun RichContentField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    val markColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val linkColor = MaterialTheme.colorScheme.primary
    val styling = remember(markColor, linkColor) {
        VisualTransformation { text ->
            // Same characters in and out, so cursor positions map one to one.
            TransformedText(RichText.styleForEditing(text.text, markColor, linkColor), OffsetMapping.Identity)
        }
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        visualTransformation = styling,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box {
                if (value.text.isEmpty()) {
                    Text("Note", style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)))
                }
                inner()
            }
        },
    )
}

/** Shown above the keyboard while the body is being edited. */
@Composable
internal fun FormattingToolbar(onAction: ((TextFieldValue) -> TextFieldValue) -> Unit) {
    Column {
        HorizontalDivider()
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
        ) {
            FormatButton(Icons.Outlined.FormatBold, "Bold") { onAction { RichText.toggleInline(it, "**") } }
            FormatButton(Icons.Outlined.FormatItalic, "Italic") { onAction { RichText.toggleInline(it, "*") } }
            FormatButton(Icons.Outlined.FormatStrikethrough, "Strikethrough") { onAction { RichText.toggleInline(it, "~~") } }
            FormatButton(Icons.Outlined.Title, "Heading") { onAction { RichText.toggleLinePrefix(it, "# ") } }
            FormatButton(Icons.AutoMirrored.Outlined.FormatListBulleted, "Bulleted list") { onAction { RichText.toggleLinePrefix(it, "- ") } }
            FormatButton(Icons.Outlined.FormatListNumbered, "Numbered list") { onAction { RichText.toggleLinePrefix(it, "1. ") } }
        }
    }
}

@Composable
private fun FormatButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
}

/**
 * Every picture full width at its own shape, so nothing is cropped away: photos,
 * screenshots and panoramas all show whole. Tap to open.
 */
@Composable
internal fun EditorImages(
    attachments: List<Attachment>,
    importing: Int,
    onOpen: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
    ) {
        attachments.forEach { image ->
            AsyncImage(
                model = AttachmentStore.file(context, image.fileName),
                contentDescription = "Image",
                // Fit, not Crop: the box already has the image's shape, and if the recorded
                // size were ever off, Fit shows the whole picture rather than cutting it.
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(image.aspectRatio(min = 0.3f, max = 3f))
                    .clip(shape)
                    .clickable { onOpen(image) },
            )
        }
        if (importing > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    if (importing == 1) "Adding image…" else "Adding $importing images…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/** Full-screen picture with pinch-to-zoom, its text, share and delete. */
@Composable
internal fun ImageViewer(
    attachment: Attachment,
    onDelete: () -> Unit,
    onAddText: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = AttachmentStore.file(context, attachment.fileName),
                contentDescription = "Image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            Surface(color = Color.Black.copy(alpha = 0.4f), contentColor = Color.White) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
                    Box(Modifier.weight(1f))
                    IconButton(onClick = { showText = true }) {
                        Icon(Icons.AutoMirrored.Outlined.TextSnippet, contentDescription = "Text in image")
                    }
                    IconButton(onClick = { context.shareImage(attachment) }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share image")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete image")
                    }
                }
            }
        }
        if (showText) {
            ImageTextDialog(
                text = attachment.ocrText,
                onAdd = { onAddText(it); showText = false; onDismiss() },
                onDismiss = { showText = false },
            )
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete image?") },
                text = { Text("The image will be removed from this note.") },
                confirmButton = {
                    TextButton(onClick = { confirmDelete = false; onDelete() }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
    }
}

/** The text read from a picture: selectable, copyable, and addable to the note. */
@Composable
private fun ImageTextDialog(text: String?, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Text in image") },
        text = {
            when {
                text == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Reading text…", modifier = Modifier.padding(start = 12.dp))
                }
                text.isEmpty() -> Text("No text found in this image.")
                else -> SelectionContainer {
                    Text(text, modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
                }
            }
        },
        confirmButton = {
            if (!text.isNullOrEmpty()) TextButton(onClick = { onAdd(text) }) { Text("Add to note") }
            else TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = if (!text.isNullOrEmpty()) {
            {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(text))
                    Toast.makeText(context, "Text copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy") }
            }
        } else null,
    )
}

private fun Context.fileProviderUri(file: File): Uri =
    FileProvider.getUriForFile(this, "$packageName.files", file)

/** A fresh cache file the camera app writes the photo into. */
internal fun cameraOutputUri(context: Context): Uri {
    val file = File(context.cacheDir, "camera/capture.jpg").apply { parentFile?.mkdirs() }
    return context.fileProviderUri(file)
}

private fun Context.shareImage(attachment: Attachment) {
    val uri = fileProviderUri(AttachmentStore.file(this, attachment.fileName))
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/jpeg")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(send, "Share image"))
}

internal fun Context.openLink(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show()
    }
}
