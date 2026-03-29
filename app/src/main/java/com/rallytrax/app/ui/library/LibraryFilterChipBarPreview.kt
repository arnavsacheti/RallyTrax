package com.rallytrax.app.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rallytrax.app.ui.theme.RallyTraxTheme

@Preview(showBackground = true, name = "With Available Filters")
@Composable
private fun LibraryFilterChipBarPreview() {
    RallyTraxTheme {
        LibraryFilterChipBar(
            uiState = LibraryUiState(
                availableDifficulties = sortedSetOf("Casual", "Moderate", "Spirited", "Expert"),
                availableSurfaces = sortedSetOf("Dirt", "Gravel", "Paved"),
                availableRouteTypes = sortedSetOf("Canyon Road", "Mountain Pass"),
            ),
            onToggleDifficulty = {},
            onToggleSurface = {},
            onToggleRouteType = {},
            onNearMeClick = {},
            onMoreFiltersClick = {},
            onClearAllFilters = {},
        )
    }
}

@Preview(showBackground = true, name = "With Active Filters")
@Composable
private fun LibraryFilterChipBarActivePreview() {
    RallyTraxTheme {
        LibraryFilterChipBar(
            uiState = LibraryUiState(
                availableDifficulties = sortedSetOf("Casual", "Moderate", "Spirited", "Expert"),
                selectedDifficulties = setOf("Spirited", "Expert"),
                availableSurfaces = sortedSetOf("Dirt", "Gravel", "Paved"),
                selectedSurfaces = setOf("Paved"),
                activeFilterCount = 3,
            ),
            onToggleDifficulty = {},
            onToggleSurface = {},
            onToggleRouteType = {},
            onNearMeClick = {},
            onMoreFiltersClick = {},
            onClearAllFilters = {},
        )
    }
}

@Preview(showBackground = true, name = "Empty (No Data)")
@Composable
private fun LibraryFilterChipBarEmptyPreview() {
    RallyTraxTheme {
        LibraryFilterChipBar(
            uiState = LibraryUiState(),
            onToggleDifficulty = {},
            onToggleSurface = {},
            onToggleRouteType = {},
            onNearMeClick = {},
            onMoreFiltersClick = {},
            onClearAllFilters = {},
        )
    }
}
