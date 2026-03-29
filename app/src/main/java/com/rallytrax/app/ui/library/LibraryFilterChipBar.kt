package com.rallytrax.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rallytrax.app.ui.theme.DifficultyAmber
import com.rallytrax.app.ui.theme.DifficultyGreen
import com.rallytrax.app.ui.theme.DifficultyOrange
import com.rallytrax.app.ui.theme.DifficultyRed
import com.rallytrax.app.ui.theme.SurfaceCobblestone
import com.rallytrax.app.ui.theme.SurfaceDirt
import com.rallytrax.app.ui.theme.SurfaceGravel
import com.rallytrax.app.ui.theme.SurfacePaved
import com.rallytrax.app.ui.theme.SurfaceUnknown

@Composable
fun LibraryFilterChipBar(
    uiState: LibraryUiState,
    onToggleDifficulty: (String) -> Unit,
    onToggleSurface: (String) -> Unit,
    onToggleRouteType: (String) -> Unit,
    onNearMeClick: () -> Unit,
    onMoreFiltersClick: () -> Unit,
    onClearAllFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeCount = uiState.activeFilterCount

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(uiState.availableDifficulties.toList()) { difficulty ->
            FilterChip(
                selected = difficulty in uiState.selectedDifficulties,
                onClick = { onToggleDifficulty(difficulty) },
                label = { Text(difficulty) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(difficultyColor(difficulty), CircleShape),
                    )
                },
            )
        }

        items(uiState.availableSurfaces.toList()) { surface ->
            FilterChip(
                selected = surface in uiState.selectedSurfaces,
                onClick = { onToggleSurface(surface) },
                label = { Text(surface) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(surfaceColor(surface), CircleShape),
                    )
                },
            )
        }

        items(uiState.availableRouteTypes.toList()) { routeType ->
            FilterChip(
                selected = routeType in uiState.selectedRouteTypes,
                onClick = { onToggleRouteType(routeType) },
                label = { Text(routeType) },
            )
        }

        item {
            FilterChip(
                selected = uiState.nearMeFilter != null,
                onClick = onNearMeClick,
                label = { Text("Near Me") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }

        item {
            FilterChip(
                selected = false,
                onClick = onMoreFiltersClick,
                label = { Text("More Filters") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }

        if (activeCount > 0) {
            item {
                AssistChip(
                    onClick = onClearAllFilters,
                    label = { Text("Clear ($activeCount)") },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}

private fun difficultyColor(difficulty: String): Color = when (difficulty) {
    "Casual" -> DifficultyGreen
    "Moderate" -> DifficultyAmber
    "Spirited" -> DifficultyOrange
    "Expert" -> DifficultyRed
    else -> Color.Gray
}

private fun surfaceColor(surface: String): Color = when (surface.lowercase()) {
    "paved", "tarmac" -> SurfacePaved
    "gravel" -> SurfaceGravel
    "dirt" -> SurfaceDirt
    "cobblestone" -> SurfaceCobblestone
    else -> SurfaceUnknown
}
