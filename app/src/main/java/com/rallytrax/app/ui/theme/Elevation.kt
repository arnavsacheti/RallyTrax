package com.rallytrax.app.ui.theme

import androidx.compose.ui.unit.dp

// Named M3 Expressive elevation tokens. Tint opacity is advisory — Compose's
// surface tint is driven via the colorScheme's surfaceTint + tonalElevation dp.
object RallyTraxElevation {
    val Level0 = 0.dp  // flat surfaces, filled buttons
    val Level1 = 1.dp  // elevated cards, resting sheets
    val Level2 = 3.dp  // navigation bar, menus
    val Level3 = 6.dp  // FABs, modal sheets, dialogs
    val Level4 = 8.dp  // elevated menus
    val Level5 = 12.dp // modal navigation drawer
}
