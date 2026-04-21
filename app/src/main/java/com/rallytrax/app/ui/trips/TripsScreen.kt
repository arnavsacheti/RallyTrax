package com.rallytrax.app.ui.trips

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rallytrax.app.ui.components.MonoText
import com.rallytrax.app.ui.components.OverlineLabel
import com.rallytrax.app.ui.theme.RallyTraxTypeEmphasized
import com.rallytrax.app.util.formatDate
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    onTripClick: (String) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: TripsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(pendingDelete?.id) {
        pendingDelete?.let { trip ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "${trip.name} deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> viewModel.cancelDeleteTrip()
                SnackbarResult.Dismissed -> viewModel.confirmDeleteTrip()
            }
        }
    }

    if (showCreateDialog) {
        CreateTripDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description ->
                viewModel.createTrip(name, description)
                showCreateDialog = false
            },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("My Trips") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create trip")
            }
        },
    ) { innerPadding ->
        val visibleTrips = remember(uiState.trips, pendingDelete) {
            val pendingId = pendingDelete?.id
            if (pendingId != null) uiState.trips.filter { it.trip.id != pendingId } else uiState.trips
        }

        if (visibleTrips.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No trips yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create a trip to group your drives together.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "totals") {
                    TripsTotalsStrip(
                        tripCount = visibleTrips.size,
                        stintCount = visibleTrips.sumOf { it.trackCount },
                        totalDistanceMeters = visibleTrips.sumOf { it.totalDistanceMeters },
                        totalDurationMs = visibleTrips.sumOf { it.totalDurationMs },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(
                    items = visibleTrips,
                    key = { it.trip.id },
                ) { summary ->
                    val dismissState = rememberSwipeToDismissBoxState()

                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            viewModel.requestDeleteTrip(summary.trip)
                            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                    }

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val bgColor by animateColorAsState(
                                when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                    else -> Color.Transparent
                                },
                                label = "swipe_bg",
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(bgColor),
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 24.dp),
                                )
                            }
                        },
                        enableDismissFromStartToEnd = false,
                        modifier = Modifier.animateItem(),
                    ) {
                        TripListItem(
                            summary = summary,
                            onClick = { onTripClick(summary.trip.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TripListItem(
    summary: TripSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateRange = tripDateRange(summary)
    val isDark = !MaterialTheme.colorScheme.background.isLight()
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.dp),
    ) {
        // Hero: stitched trip map + gradient scrim + title + pills
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            StitchedTripMap(
                tripId = summary.trip.id,
                stintCount = summary.trackCount.coerceAtLeast(1),
                dayCount = summary.dayCount.coerceAtLeast(1),
                isDark = isDark,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xB00C0E12)),
                            startY = 60f,
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                dateRange?.let {
                    TripPill(
                        text = it,
                        leadingIcon = Icons.Filled.CalendarMonth,
                        bg = Color(0xD8FFFFFF),
                        fg = Color(0xFF1B1D22),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            ) {
                Text(
                    text = summary.trip.name,
                    style = RallyTraxTypeEmphasized.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                summary.trip.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xCFFFFFFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Body: metric row with dividers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TripMetric(value = formatDistance(summary.totalDistanceMeters), label = "Distance")
            TripMetricDivider()
            TripMetric(
                value = if (summary.totalDurationMs > 0) formatElapsedTime(summary.totalDurationMs) else "—",
                label = "Time",
            )
            TripMetricDivider()
            TripMetric(value = summary.trackCount.toString(), label = "Stints")
            if (summary.dayCount > 1) {
                TripMetricDivider()
                TripMetric(value = summary.dayCount.toString(), label = "Days")
            }
        }
    }
}

@Composable
private fun TripMetric(value: String, label: String) {
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
private fun TripMetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun TripPill(
    text: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    bg: Color,
    fg: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        leadingIcon?.let {
            Icon(imageVector = it, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        }
        Text(text = text, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun tripDateRange(summary: TripSummary): String? {
    val first = summary.firstRecordedAt ?: return null
    val last = summary.lastRecordedAt ?: return formatDate(first)
    return if (last - first < 24L * 3600 * 1000) formatDate(first)
    else "${formatDate(first)} – ${formatDate(last)}"
}

private fun Color.isLight(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) > 0.5f

@Composable
private fun TripsTotalsStrip(
    tripCount: Int,
    stintCount: Int,
    totalDistanceMeters: Double,
    totalDurationMs: Long,
    modifier: Modifier = Modifier,
) {
    com.rallytrax.app.ui.components.HeroGradientCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 16.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TripsTotalCell(label = "Trips", value = tripCount.toString())
            TripsTotalCell(label = "Stints", value = stintCount.toString())
            TripsTotalCell(label = "Distance", value = formatDistance(totalDistanceMeters))
            if (totalDurationMs > 0) {
                TripsTotalCell(label = "Time", value = formatElapsedTime(totalDurationMs))
            }
        }
    }
}

@Composable
private fun TripsTotalCell(label: String, value: String) {
    Column {
        com.rallytrax.app.ui.components.MonoText(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        com.rallytrax.app.ui.components.OverlineLabel(
            text = label,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun CreateTripDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Trip") },
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
                onClick = { onCreate(name, description.ifBlank { null }) },
                enabled = name.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
