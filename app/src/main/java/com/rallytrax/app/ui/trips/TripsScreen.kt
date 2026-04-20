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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = summary.trip.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatDate(summary.trip.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            summary.trip.description?.let { desc ->
                if (desc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TripStatChip(
                    label = "Stints",
                    value = "${summary.trackCount}",
                )
                TripStatChip(
                    label = "Distance",
                    value = formatDistance(summary.totalDistanceMeters),
                )
                if (summary.totalDurationMs > 0) {
                    TripStatChip(
                        label = "Duration",
                        value = formatElapsedTime(summary.totalDurationMs),
                    )
                }
            }
        }
    }
}

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
private fun TripStatChip(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
