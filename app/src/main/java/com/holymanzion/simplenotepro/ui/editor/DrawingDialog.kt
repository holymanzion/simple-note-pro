package com.holymanzion.simplenotepro.ui.editor

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** One finger stroke. [points] grows while the finger moves, which redraws the canvas. */
private class DrawStroke(val color: Color, val widthDp: Float) {
    val points: SnapshotStateList<Offset> = mutableStateListOf()
}

private val PenColors = listOf(
    "Black" to Color(0xFF1F1B16),
    "Red" to Color(0xFFD32F2F),
    "Blue" to Color(0xFF1565C0),
    "Green" to Color(0xFF2E7D32),
    "Orange" to Color(0xFFEF6C00),
    "Purple" to Color(0xFF6A1B9A),
)
private val PenSizes = listOf("Thin" to 3f, "Medium" to 6f, "Thick" to 12f)

/**
 * Full-screen finger drawing on white paper. "Save" hands back the picture, at the
 * canvas's own pixel size; the caller owns (and must recycle) the bitmap.
 */
@Composable
internal fun DrawingDialog(onSave: (Bitmap) -> Unit, onDismiss: () -> Unit) {
    val strokes = remember { mutableStateListOf<DrawStroke>() }
    var color by remember { mutableStateOf(PenColors.first().second) }
    var penWidth by remember { mutableStateOf(PenSizes[1].second) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density

    fun close() = if (strokes.isEmpty()) onDismiss() else confirmDiscard = true

    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        BackHandler(onBack = ::close)
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    IconButton(onClick = ::close) { Icon(Icons.Outlined.Close, contentDescription = "Close drawing") }
                    Text("Drawing", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { strokes.removeLastOrNull() }, enabled = strokes.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo stroke")
                    }
                    IconButton(onClick = { strokes.clear() }, enabled = strokes.isNotEmpty()) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear drawing")
                    }
                    TextButton(
                        onClick = {
                            if (canvasSize.width > 0 && canvasSize.height > 0) {
                                onSave(render(strokes, canvasSize, density))
                            }
                        },
                        enabled = strokes.isNotEmpty(),
                        modifier = Modifier.testTag("drawing-save"),
                    ) { Text("Save") }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        .onSizeChanged { canvasSize = it }
                        .testTag("drawing-canvas")
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val stroke = DrawStroke(color, penWidth).also { it.points.add(down.position) }
                                strokes.add(stroke)
                                down.consume()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    stroke.points.add(change.position)
                                    change.consume()
                                }
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        strokes.forEach { stroke ->
                            val width = stroke.widthDp * density
                            val points = stroke.points
                            if (points.size == 1) {
                                drawCircle(stroke.color, radius = width / 2, center = points[0])
                            } else if (points.size > 1) {
                                drawPath(
                                    composePath(points),
                                    stroke.color,
                                    style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    PenColors.forEach { (name, c) ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(c, CircleShape)
                                .border(
                                    width = if (c == color) 3.dp else 1.dp,
                                    color = if (c == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                )
                                .semantics { contentDescription = "$name pen"; selected = c == color }
                                .clickable { color = c },
                        )
                    }
                    Box(Modifier.size(width = 8.dp, height = 1.dp))
                    PenSizes.forEach { (name, w) ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .border(
                                    width = if (w == penWidth) 3.dp else 1.dp,
                                    color = if (w == penWidth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                )
                                .semantics { contentDescription = "$name pen"; selected = w == penWidth }
                                .clickable { penWidth = w },
                        ) {
                            Box(Modifier.size((w + 2).dp).background(MaterialTheme.colorScheme.onSurface, CircleShape))
                        }
                    }
                }
            }
        }
        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = { Text("Discard drawing?") },
                text = { Text("Your drawing hasn't been added to the note.") },
                confirmButton = {
                    TextButton(onClick = { confirmDiscard = false; onDismiss() }) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep drawing") } },
            )
        }
    }
}

/** A path through [points] using midpoints as curve ends, so lines come out smooth. */
private fun composePath(points: List<Offset>) = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        quadraticTo(prev.x, prev.y, (prev.x + cur.x) / 2, (prev.y + cur.y) / 2)
    }
    lineTo(points.last().x, points.last().y)
}

/** The same curve as [composePath], for drawing onto a bitmap. */
private fun androidPath(points: List<Offset>) = android.graphics.Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        quadTo(prev.x, prev.y, (prev.x + cur.x) / 2, (prev.y + cur.y) / 2)
    }
    lineTo(points.last().x, points.last().y)
}

/** Draws the strokes onto a white bitmap the size of the canvas. */
private fun render(strokes: List<DrawStroke>, size: IntSize, density: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    strokes.forEach { stroke ->
        val points = stroke.points.toList()
        if (points.isEmpty()) return@forEach
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color.toArgb()
            strokeWidth = stroke.widthDp * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (points.size == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(points[0].x, points[0].y, paint.strokeWidth / 2, paint)
        } else {
            paint.style = Paint.Style.STROKE
            canvas.drawPath(androidPath(points), paint)
        }
    }
    return bitmap
}
