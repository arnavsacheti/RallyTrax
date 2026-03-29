package com.rallytrax.app.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.ui.theme.RallyTraxTheme

private val sampleTrackFull = TrackEntity(
    id = "preview-1",
    name = "Nockalmstraße",
    recordedAt = 1711000000000L,
    distanceMeters = 34500.0,
    durationMs = 2700000L,
    avgSpeedMps = 12.8,
    elevationGainM = 1230.0,
    difficultyRating = "Spirited",
    routeType = "Mountain Pass",
    surfaceBreakdown = "Paved:85,Gravel:10,Dirt:5",
    primarySurface = "Paved",
    tags = "Solo Drive,Photography Run",
)

private val sampleTrackMinimal = TrackEntity(
    id = "preview-2",
    name = "Mt Fuji Touge (Fuji Subaru Line)",
    recordedAt = 1711000000000L,
    distanceMeters = 24320.0,
    durationMs = 760000L,
)

@Preview(showBackground = true, name = "Full Metadata")
@Composable
private fun EnhancedTrackListItemFullPreview() {
    RallyTraxTheme {
        EnhancedTrackListItem(
            track = sampleTrackFull,
            isSelected = false,
            isMultiSelectMode = false,
            onClick = {},
            onLongClick = {},
            unitSystem = UnitSystem.METRIC,
            attemptCount = 3,
        )
    }
}

@Preview(showBackground = true, name = "Minimal (GPX Import)")
@Composable
private fun EnhancedTrackListItemMinimalPreview() {
    RallyTraxTheme {
        EnhancedTrackListItem(
            track = sampleTrackMinimal,
            isSelected = false,
            isMultiSelectMode = false,
            onClick = {},
            onLongClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Selected State")
@Composable
private fun EnhancedTrackListItemSelectedPreview() {
    RallyTraxTheme {
        EnhancedTrackListItem(
            track = sampleTrackFull,
            isSelected = true,
            isMultiSelectMode = true,
            onClick = {},
            onLongClick = {},
        )
    }
}

@Preview(showBackground = true, name = "All Difficulties")
@Composable
private fun EnhancedTrackListItemDifficultiesPreview() {
    RallyTraxTheme {
        Column {
            listOf("Casual", "Moderate", "Spirited", "Expert").forEach { diff ->
                EnhancedTrackListItem(
                    track = sampleTrackFull.copy(
                        id = "diff-$diff",
                        difficultyRating = diff,
                    ),
                    isSelected = false,
                    isMultiSelectMode = false,
                    onClick = {},
                    onLongClick = {},
                )
            }
        }
    }
}
