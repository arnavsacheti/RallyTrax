package com.rallytrax.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Seed color: #1A73E8 (Rally Blue)

// ── Primary palette (HCT hue ~260°, chroma ~48) ─────────────────────────────
val PrimaryLight = Color(0xFF1A73E8)          // tone 40
val OnPrimaryLight = Color(0xFFFFFFFF)        // tone 100
val PrimaryContainerLight = Color(0xFFD3E3FD) // tone 90
val OnPrimaryContainerLight = Color(0xFF001D36) // tone 10

val PrimaryDark = Color(0xFFA8C8FF)           // tone 80
val OnPrimaryDark = Color(0xFF003258)         // tone 20
val PrimaryContainerDark = Color(0xFF00497D)  // tone 30
val OnPrimaryContainerDark = Color(0xFFD3E3FD) // tone 90

// ── Secondary palette (chroma ~16, same hue) ─────────────────────────────────
val SecondaryLight = Color(0xFF535F70)         // tone 40
val OnSecondaryLight = Color(0xFFFFFFFF)       // tone 100
val SecondaryContainerLight = Color(0xFFD7E3F8) // tone 90
val OnSecondaryContainerLight = Color(0xFF101C2B) // tone 10

val SecondaryDark = Color(0xFFBBC7DB)          // tone 80
val OnSecondaryDark = Color(0xFF253140)        // tone 20
val SecondaryContainerDark = Color(0xFF3C4858) // tone 30
val OnSecondaryContainerDark = Color(0xFFD7E3F8) // tone 90

// ── Tertiary palette (hue +60°, chroma ~24) ──────────────────────────────────
val TertiaryLight = Color(0xFF6B5778)          // tone 40
val OnTertiaryLight = Color(0xFFFFFFFF)        // tone 100
val TertiaryContainerLight = Color(0xFFF3DAFF) // tone 90
val OnTertiaryContainerLight = Color(0xFF251431) // tone 10

val TertiaryDark = Color(0xFFD7BEE4)           // tone 80
val OnTertiaryDark = Color(0xFF3B2948)         // tone 20
val TertiaryContainerDark = Color(0xFF533F5F)  // tone 30
val OnTertiaryContainerDark = Color(0xFFF3DAFF) // tone 90

// ── Error palette ────────────────────────────────────────────────────────────
val ErrorLight = Color(0xFFBA1A1A)             // tone 40
val OnErrorLight = Color(0xFFFFFFFF)           // tone 100
val ErrorContainerLight = Color(0xFFFFDAD6)    // tone 90
val OnErrorContainerLight = Color(0xFF410002)  // tone 10

val ErrorDark = Color(0xFFFFB4AB)              // tone 80
val OnErrorDark = Color(0xFF690005)            // tone 20
val ErrorContainerDark = Color(0xFF93000A)     // tone 30
val OnErrorContainerDark = Color(0xFFFFDAD6)   // tone 90

// ── Neutral palette (chroma ~4) ──────────────────────────────────────────────
val BackgroundLight = Color(0xFFFAFBFF)        // tone 98
val OnBackgroundLight = Color(0xFF1A1C20)      // tone 10
val SurfaceLight = Color(0xFFFAFBFF)           // tone 98
val OnSurfaceLight = Color(0xFF1A1C20)         // tone 10

val SurfaceContainerLowestLight = Color(0xFFFFFFFF) // tone 100
val SurfaceContainerLowLight = Color(0xFFF4F5FA)    // tone 96
val SurfaceContainerLight = Color(0xFFEEEFF4)       // tone 94
val SurfaceContainerHighLight = Color(0xFFE8E9EF)   // tone 92
val SurfaceContainerHighestLight = Color(0xFFE3E4E9) // tone 90
val SurfaceDimLight = Color(0xFFDADBE0)             // tone 87
val SurfaceBrightLight = Color(0xFFFAFBFF)          // tone 98

val BackgroundDark = Color(0xFF111318)         // tone 6
val OnBackgroundDark = Color(0xFFE2E2E9)       // tone 90
val SurfaceDark = Color(0xFF111318)            // tone 6
val OnSurfaceDark = Color(0xFFE2E2E9)          // tone 90

val SurfaceContainerLowestDark = Color(0xFF0C0E13)  // tone 4
val SurfaceContainerLowDark = Color(0xFF1A1C20)     // tone 10
val SurfaceContainerDark = Color(0xFF1E2025)        // tone 12
val SurfaceContainerHighDark = Color(0xFF282A2F)    // tone 17
val SurfaceContainerHighestDark = Color(0xFF33353A)  // tone 22
val SurfaceDimDark = Color(0xFF111318)              // tone 6
val SurfaceBrightDark = Color(0xFF37393E)           // tone 24

// ── Neutral variant palette (chroma ~8) ──────────────────────────────────────
val SurfaceVariantLight = Color(0xFFDFE2EB)    // tone 90
val OnSurfaceVariantLight = Color(0xFF43474E)  // tone 30
val OutlineLight = Color(0xFF73777F)           // tone 50
val OutlineVariantLight = Color(0xFFC3C6CF)    // tone 80

val SurfaceVariantDark = Color(0xFF43474E)     // tone 30
val OnSurfaceVariantDark = Color(0xFFC3C6CF)   // tone 80
val OutlineDark = Color(0xFF8D9199)            // tone 60
val OutlineVariantDark = Color(0xFF43474E)     // tone 30

// ── Inverse & scrim ──────────────────────────────────────────────────────────
val InverseSurfaceLight = Color(0xFF2F3036)    // tone 20
val InverseOnSurfaceLight = Color(0xFFF1F0F7) // tone 95
val InversePrimaryLight = Color(0xFFA8C8FF)   // tone 80

val InverseSurfaceDark = Color(0xFFE2E2E9)     // tone 90
val InverseOnSurfaceDark = Color(0xFF2F3036)   // tone 20
val InversePrimaryDark = Color(0xFF1A73E8)     // tone 40

val ScrimColor = Color(0xFF000000)             // tone 0

// ── Map layer tokens (constant across light/dark themes) ─────────────────────
val LayerSpeedLow = Color(0xFF34A853)
val LayerSpeedMid = Color(0xFFFBBC04)
val LayerSpeedHigh = Color(0xFFEA4335)
val LayerAccel = Color(0xFF1A73E8)
val LayerDecel = Color(0xFFE8710A)
val LayerElevation = Color(0xFF9334E6)
val LayerCurvature = Color(0xFFE91E63)
val LayerCallout = Color(0xFF202124)
val HeatmapCold = Color(0xFF4285F4)
val HeatmapHot = Color(0xFFEA4335)

// ── Surface layer colors ─────────────────────────────────────────────────────
val SurfacePaved = Color(0xFF616161)
val SurfaceGravel = Color(0xFFA1887F)
val SurfaceDirt = Color(0xFF8D6E63)
val SurfaceCobblestone = Color(0xFF78909C)
val SurfaceUnknown = Color(0xFFBDBDBD)

// ── Difficulty colors ────────────────────────────────────────────────────────
val DifficultyGreen = Color(0xFF34A853)
val DifficultyAmber = Color(0xFFFBBC04)
val DifficultyOrange = Color(0xFFE8710A)
val DifficultyRed = Color(0xFFEA4335)

// ── Dashboard warning light colors ──────────────────────────────────────────
val WarningAmber = Color(0xFFFFB300)
val WarningRed = Color(0xFFEF5350)

// ── M3 Expressive: Custom semantic colors via CompositionLocal ─────────────

@Immutable
data class RallyTraxColors(
    val fuelWarning: Color = WarningAmber,
    val fuelCritical: Color = WarningRed,
    val maintenanceDue: Color = WarningRed,
    val maintenanceWarning: Color = WarningAmber,
    val speedSafe: Color = LayerSpeedLow,
    val speedDanger: Color = LayerSpeedHigh,
    val surfaceGravel: Color = SurfaceGravel,
    val surfaceTarmac: Color = SurfacePaved,
    val surfaceDirt: Color = SurfaceDirt,
    val recordingActive: Color = Color(0xFFFF1744),
)

val LightRallyTraxColors = RallyTraxColors()

val DarkRallyTraxColors = RallyTraxColors(
    fuelWarning = Color(0xFFFFCA28),
    fuelCritical = Color(0xFFFF8A80),
    maintenanceDue = Color(0xFFFF8A80),
    maintenanceWarning = Color(0xFFFFCA28),
    speedSafe = Color(0xFF69F0AE),
    speedDanger = Color(0xFFFF8A80),
    surfaceGravel = Color(0xFFBCAAA4),
    surfaceTarmac = Color(0xFF9E9E9E),
    surfaceDirt = Color(0xFFA1887F),
    recordingActive = Color(0xFFFF5252),
)

val LocalRallyTraxColors = staticCompositionLocalOf { RallyTraxColors() }
