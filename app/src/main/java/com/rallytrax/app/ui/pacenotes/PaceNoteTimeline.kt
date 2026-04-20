package com.rallytrax.app.ui.pacenotes

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rallytrax.app.data.local.entity.NoteType
import com.rallytrax.app.data.local.entity.PaceNoteEntity
import com.rallytrax.app.ui.components.OverlineLabel
import com.rallytrax.app.ui.theme.LocalRallyTraxColors
import com.rallytrax.app.ui.theme.ShapeSignature
import kotlin.math.roundToInt

/**
 * Adaptive pace-note timeline. Three zoom levels filter note density; glyph size
 * also scales. Tapping a glyph invokes [onSelectNote] with the note index.
 *
 * Design: prototype's charts.jsx PaceNoteTimeline + route-screen S|M|L stepper.
 */
enum class PaceZoom(val glyphDp: Dp, val severityFloor: Int, val showNumber: Boolean) {
    Overview(20.dp, 4, false),
    Mid(26.dp, 2, false),
    Detail(32.dp, 1, true),
}

private fun includeInZoom(zoom: PaceZoom, note: PaceNoteEntity): Boolean {
    val isTurnType = when (note.noteType) {
        NoteType.LEFT, NoteType.RIGHT,
        NoteType.HAIRPIN_LEFT, NoteType.HAIRPIN_RIGHT,
        NoteType.SQUARE_LEFT, NoteType.SQUARE_RIGHT -> true
        else -> false
    }
    val isVertical = note.noteType == NoteType.CREST || note.noteType == NoteType.DIP ||
        note.noteType == NoteType.BIG_CREST || note.noteType == NoteType.BIG_DIP
    return when (zoom) {
        PaceZoom.Overview -> if (isTurnType) note.severity >= 4 else note.noteType == NoteType.BIG_CREST || note.noteType == NoteType.BIG_DIP
        PaceZoom.Mid -> if (isTurnType) note.severity >= 2 else isVertical
        PaceZoom.Detail -> true
    }
}

@Composable
fun PaceNoteTimelineCard(
    notes: List<PaceNoteEntity>,
    totalDistanceMeters: Double,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSelectNote: (Int) -> Unit = {},
    onExpandList: () -> Unit = {},
) {
    var zoom by remember { mutableStateOf(PaceZoom.Mid) }
    Box(
        modifier = modifier
            .clip(ShapeSignature)
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OverlineLabel(text = "Pace notes · ${notes.size}", modifier = Modifier.weight(1f))
                ZoomStepper(zoom = zoom, onChange = { zoom = it })
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onExpandList() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Expand pace notes",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            TimelineTrack(
                notes = notes,
                totalDistanceMeters = totalDistanceMeters,
                zoom = zoom,
                selectedIndex = selectedIndex,
                onSelectNote = onSelectNote,
            )
            LegendBar()
        }
    }
}

@Composable
private fun ZoomStepper(zoom: PaceZoom, onChange: (PaceZoom) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf("S" to PaceZoom.Overview, "M" to PaceZoom.Mid, "L" to PaceZoom.Detail).forEach { (label, z) ->
            val selected = zoom == z
            Box(
                modifier = Modifier
                    .size(width = 26.dp, height = 26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onChange(z) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TimelineTrack(
    notes: List<PaceNoteEntity>,
    totalDistanceMeters: Double,
    zoom: PaceZoom,
    selectedIndex: Int?,
    onSelectNote: (Int) -> Unit,
) {
    val visible = remember(notes, zoom) {
        notes.withIndex().filter { includeInZoom(zoom, it.value) }
    }
    val total = totalDistanceMeters.coerceAtLeast(1.0)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (zoom == PaceZoom.Detail) 72.dp else 56.dp),
    ) {
        val trackWidth = maxWidth
        val glyph = zoom.glyphDp
        val outline = MaterialTheme.colorScheme.outlineVariant
        // centerline
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(2.dp)
                .background(outline),
        )
        // km-tick marks
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { f ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = trackWidth * f - 0.5.dp, y = (-8).dp)
                    .size(width = 1.dp, height = 16.dp)
                    .background(outline.copy(alpha = 0.5f)),
            )
        }
        visible.forEach { (originalIndex, note) ->
            val f = ((note.distanceFromStart / total).toFloat()).coerceIn(0f, 1f)
            val xOffset = trackWidth * f - glyph / 2
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = xOffset)
                    .size(glyph)
                    .clickable { onSelectNote(originalIndex) },
                contentAlignment = Alignment.Center,
            ) {
                if (selectedIndex == originalIndex) {
                    Box(
                        modifier = Modifier
                            .size(glyph * 1.15f)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    )
                }
                PaceNoteGlyph(noteType = note.noteType, severity = note.severity, sizeDp = glyph)
            }
        }
    }
}

@Composable
private fun LegendBar() {
    val rt = LocalRallyTraxColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "1",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(rt.sevLow, rt.sevMid, rt.sevHigh),
                    ),
                ),
        )
        Text(
            text = "6",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
