package com.rallytrax.app.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.ui.components.MonoText
import com.rallytrax.app.ui.components.OverlineLabel
import com.rallytrax.app.ui.theme.DifficultyAmber
import com.rallytrax.app.ui.theme.DifficultyGreen
import com.rallytrax.app.ui.theme.DifficultyOrange
import com.rallytrax.app.ui.theme.DifficultyRed
import com.rallytrax.app.ui.theme.RallyTraxTypeEmphasized
import com.rallytrax.app.util.formatDate
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    onBack: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onShare: () -> Unit = {},
    viewModel: TripDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditDialog by remember { mutableStateOf(false) }
    val isDark = !MaterialTheme.colorScheme.background.isLightDetail()

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (showEditDialog) {
        uiState.trip?.let { trip ->
            EditTripDialog(
                currentName = trip.name,
                currentDescription = trip.description,
                onDismiss = { showEditDialog = false },
                onSave = { name, description ->
                    viewModel.updateTrip(name, description)
                    showEditDialog = false
                },
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Hero
            item {
                val trip = uiState.trip
                val tracks = uiState.tracks
                val first = tracks.minOfOrNull { it.recordedAt }
                val last = tracks.maxOfOrNull { it.recordedAt }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    if (trip != null) {
                        StitchedTripMap(
                            tripId = trip.id,
                            tracks = tracks,
                            isDark = isDark,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x59000000),
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background,
                                    ),
                                ),
                            ),
                    )

                    // Top controls
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        HeroIconBtn(icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HeroIconBtn(icon = Icons.Filled.BookmarkBorder, onClick = {})
                            HeroIconBtn(icon = Icons.Filled.Share, onClick = onShare)
                            HeroIconBtn(icon = Icons.Filled.Edit, onClick = { showEditDialog = true })
                        }
                    }

                    // Title overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 78.dp),
                    ) {
                        first?.let { f ->
                            val pillText = if (last != null && last - f >= 24L * 3600 * 1000) {
                                "${formatDate(f)} – ${formatDate(last)}"
                            } else formatDate(f)
                            HeroPill(
                                text = pillText,
                                icon = Icons.Filled.CalendarMonth,
                                bg = Color(0xD8FFFFFF),
                                fg = Color(0xFF1B1D22),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = trip?.name ?: "",
                            style = RallyTraxTypeEmphasized.displayMedium,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        trip?.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xD0FFFFFF),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Floating stats card (overlaps hero via negative offset)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-40).dp)
                        .padding(horizontal = 20.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                        ),
                        tonalElevation = 2.dp,
                        shadowElevation = if (isDark) 10.dp else 6.dp,
                    ) {
                        val dayCount = remember(uiState.tracks) { distinctLocalDays(uiState.tracks) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            DetailStat(label = "Distance", value = formatDistance(uiState.totalDistanceMeters))
                            DetailStat(
                                label = "Time",
                                value = if (uiState.totalDurationMs > 0) formatElapsedTime(uiState.totalDurationMs) else "—",
                            )
                            DetailStat(label = "Stints", value = uiState.trackCount.toString())
                            DetailStat(label = "Days", value = dayCount.coerceAtLeast(1).toString())
                        }
                    }
                }
            }

            // Timeline header
            item {
                Text(
                    text = "Timeline",
                    style = RallyTraxTypeEmphasized.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp),
                )
            }

            // Timeline legs (grouped by local day)
            if (uiState.tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No stints in this trip yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                val legs = groupByLocalDay(uiState.tracks)
                legs.forEachIndexed { dayIndex, leg ->
                    item(key = "leg-header-$dayIndex") {
                        TimelineLegHeader(
                            dayNumber = dayIndex + 1,
                            label = formatDate(leg.first().recordedAt),
                            stintCount = leg.size,
                        )
                    }
                    leg.forEachIndexed { idx, track ->
                        item(key = "stint-${track.id}") {
                            TimelineStintRow(
                                track = track,
                                onClick = { onTrackClick(track.id) },
                                onRemove = { viewModel.removeTrackFromTrip(track.id) },
                            )
                            if (idx < leg.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    item(key = "leg-spacer-$dayIndex") {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String) {
    Column {
        MonoText(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OverlineLabel(text = label)
    }
}

@Composable
private fun HeroIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0xD0FFFFFF),
        contentColor = Color(0xFF1B1D22),
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun HeroPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bg: Color,
    fg: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        Text(text = text, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TimelineLegHeader(dayNumber: Int, label: String, stintCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            MonoText(
                text = dayNumber.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            text = label,
            style = RallyTraxTypeEmphasized.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        MonoText(
            text = "$stintCount stint${if (stintCount == 1) "" else "s"}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimelineStintRow(
    track: TrackEntity,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val diffColor = when (track.difficultyRating?.lowercase()) {
        "green", "easy" -> DifficultyGreen
        "amber", "moderate" -> DifficultyAmber
        "orange", "hard" -> DifficultyOrange
        "red", "expert" -> DifficultyRed
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.44f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(diffColor),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (track.trackCategory == "route") {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MonoText(
                        text = formatDistance(track.distanceMeters),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MonoText(
                        text = "·",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MonoText(
                        text = formatElapsedTime(track.durationMs),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.RemoveCircleOutline,
                    contentDescription = "Remove from trip",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun EditTripDialog(
    currentName: String,
    currentDescription: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String?) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    var description by remember { mutableStateOf(currentDescription ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Trip") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Trip name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, description.ifBlank { null }) },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun groupByLocalDay(tracks: List<TrackEntity>): List<List<TrackEntity>> {
    if (tracks.isEmpty()) return emptyList()
    val sorted = tracks.sortedBy { it.recordedAt }
    val out = mutableListOf<MutableList<TrackEntity>>()
    val cal = Calendar.getInstance()
    var currentKey: Triple<Int, Int, Int>? = null
    for (tr in sorted) {
        cal.timeInMillis = tr.recordedAt
        val key = Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        if (key != currentKey) {
            out.add(mutableListOf())
            currentKey = key
        }
        out.last().add(tr)
    }
    return out
}

private fun distinctLocalDays(tracks: List<TrackEntity>): Int {
    val cal = Calendar.getInstance()
    val keys = HashSet<Triple<Int, Int, Int>>()
    for (tr in tracks) {
        cal.timeInMillis = tr.recordedAt
        keys.add(Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)))
    }
    return keys.size
}

private fun Color.isLightDetail(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) > 0.5f
