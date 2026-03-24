# RallyTrax

Native Android driving/rally app built with Kotlin and Jetpack Compose on Material 3 Expressive. Records GPS tracks, generates pace notes, replays routes with audio co-driver support. Targets phone, tablet/foldable, Wear OS, and Android Auto.

## Tech Stack

- **Language**: Kotlin 2.1
- **UI**: Jetpack Compose + Material 3 (M3 Expressive)
- **DI**: Hilt
- **Database**: Room
- **Preferences**: DataStore
- **Maps**: Google Maps + OSMDroid
- **Auth**: Firebase (Google Sign-In)
- **Navigation**: Compose Navigation with type-safe routes (kotlinx.serialization)
- **Background**: WorkManager, foreground service for GPS recording
- **Build**: Gradle 8.9 with version catalog (`gradle/libs.versions.toml`)
- **Min SDK**: 29 (Android 10) | **Target SDK**: 35 (Android 15)

## Project Structure

```
app/src/main/java/com/rallytrax/app/
├── MainActivity.kt                 # Entry point, bottom nav, theme setup
├── RallyTraxApplication.kt        # Hilt app, WorkManager config
├── data/                           # Room entities, DAOs, DataStore preferences
├── di/                             # Hilt dependency injection modules
├── navigation/                     # Type-safe routes (Screen.kt, RallyTraxNavigation.kt)
├── pacenotes/                      # Pace note generation pipeline
├── recording/                      # GPS capture, foreground service
├── replay/                         # Replay engine & audio manager
├── ui/
│   ├── theme/                      # Design tokens (Color, Type, Shape, Motion, Theme)
│   ├── components/                 # Reusable composables
│   └── screens/                    # Feature screens
├── update/                         # In-app update checker
└── util/                           # FormatUtils, URL parsing
```

## Design System

All design tokens are in `app/src/main/java/com/rallytrax/app/ui/theme/`. Follow Material 3 Expressive conventions from Google I/O 2025.

### Colors (`Color.kt`)

- **Seed color**: `#1A73E8` (Rally Blue)
- Five HCT-derived palettes: Primary (chroma 48), Secondary (chroma 16), Tertiary (hue +60, chroma 24), Neutral (chroma 4), Neutral Variant (chroma 8)
- Each palette has 13 tonal stops (0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 99, 100)
- Light theme uses tone 40 for primary; dark theme uses tone 80
- Supports Material You dynamic colors on Android 12+ (see `Theme.kt`)

**Core M3 color roles** — always use `MaterialTheme.colorScheme.*`:
- `primary` / `onPrimary` / `primaryContainer` / `onPrimaryContainer`
- `secondary` / `onSecondary` / `secondaryContainer` / `onSecondaryContainer`
- `tertiary` / `onTertiary` / `tertiaryContainer` / `onTertiaryContainer`
- `error` / `onError` / `errorContainer` / `onErrorContainer`
- `surface` / `onSurface` / `surfaceContainerLowest` through `surfaceContainerHighest`
- `surfaceDim` / `surfaceBright` / `outline` / `outlineVariant`
- `inverseSurface` / `inverseOnSurface` / `inversePrimary` / `scrim`

**Custom semantic colors** — access via `MaterialTheme.rallyTraxColors.*`:
- `fuelWarning` (amber) / `fuelCritical` (red)
- `maintenanceDue` / `maintenanceWarning`
- `speedSafe` (green) / `speedDanger` (red)
- `surfaceGravel` / `surfaceTarmac` / `surfaceDirt`
- `recordingActive` (bright red pulse)

**Map layer tokens** (constant across themes): `LayerSpeedLow` (green), `LayerSpeedMid` (amber), `LayerSpeedHigh` (red), `LayerAccel` (blue), `LayerDecel` (orange), `LayerElevation` (purple), `LayerCurvature` (pink), `LayerCallout` (dark).

**Difficulty colors**: `DifficultyGreen`, `DifficultyAmber`, `DifficultyOrange`, `DifficultyRed`.

**Surface type colors**: `SurfacePaved`, `SurfaceGravel`, `SurfaceDirt`, `SurfaceCobblestone`, `SurfaceUnknown`.

### Typography (`Type.kt`)

15-style M3 type scale in `RallyTraxTypography`:

| Style | Size | Weight | Usage |
|-------|------|--------|-------|
| displayLarge | 57sp | 400 | Hero metrics (speed during activity) |
| displayMedium | 45sp | 400 | Dashboard hero numbers |
| displaySmall | 36sp | 400 | Card hero metrics |
| headlineLarge | 32sp | 400 | Screen titles |
| headlineMedium | 28sp | 400 | Section headers |
| headlineSmall | 24sp | 400 | Dialog titles |
| titleLarge | 22sp | 400 | App bar titles |
| titleMedium | 16sp | 500 | Card titles |
| titleSmall | 14sp | 500 | Subsection titles |
| bodyLarge | 16sp | 400 | Primary reading text |
| bodyMedium | 14sp | 400 | Secondary text |
| bodySmall | 12sp | 400 | Captions |
| labelLarge | 14sp | 500 | Buttons, prominent labels |
| labelMedium | 12sp | 500 | Chips, navigation labels |
| labelSmall | 11sp | 500 | Badges, timestamps |

**Emphasized variants** in `RallyTraxTypeEmphasized` use Medium/SemiBold weights for accent hierarchy. Use for hero stats and important numbers.

### Shapes (`Shape.kt`)

Standard M3 shapes in `RallyTraxShapes`:
- extraSmall: 4dp (badges, snackbar, text fields)
- small: 8dp (chips, small cards)
- medium: 12dp (cards default, small FABs)
- large: 16dp (FABs, large cards, nav drawer)
- extraLarge: 24dp (bottom sheets, dialogs, large FABs)

M3 Expressive extended tokens:
- `ShapeLargeIncreased`: 20dp
- `ShapeExtraLargeIncreased`: 32dp
- `ShapeExtraExtraLarge`: 48dp
- `ShapeFullRound`: 50% (buttons, search bar, sliders — pill shape)

### Motion (`Motion.kt`)

Spring-based animation via `RallyTraxMotion`:

| Method | Damping | Stiffness | Use |
|--------|---------|-----------|-----|
| `fastSpatial()` | 0.9 | 1400 | Small components (switches, buttons) |
| `fastEffects()` | 1.0 | 3800 | Small component color/opacity |
| `defaultSpatial()` | 0.9 | 700 | Partial-screen (sheets, drawers) |
| `defaultEffects()` | 1.0 | 1600 | Partial-screen color/opacity |
| `slowSpatial()` | 0.9 | 300 | Full-screen transitions |
| `slowEffects()` | 1.0 | 800 | Full-screen color/opacity |

Two motion schemes:
- **Expressive** (damping ~0.9): noticeable bounce/overshoot — default for interactive elements
- **Standard** (damping ~1.0): minimal bounce, calm — use during active recording to reduce distraction

### Theme (`Theme.kt`)

- `RallyTraxTheme` wraps `MaterialTheme` with custom colors, shapes, typography
- Supports `ThemeMode.SYSTEM`, `ThemeMode.LIGHT`, `ThemeMode.DARK`
- Dynamic color enabled by default on Android 12+; falls back to static Rally Blue palette
- Custom colors provided via `CompositionLocalProvider(LocalRallyTraxColors)`
- Access custom colors: `MaterialTheme.rallyTraxColors.fuelWarning`

### Elevation

Prefer tonal elevation over shadow. Five levels:

| Level | Shadow | Tint Opacity | Use |
|-------|--------|-------------|-----|
| 0 | 0dp | 0% | Flat surfaces, filled buttons |
| 1 | 1dp | 5% | Elevated cards, resting sheets |
| 2 | 3dp | 8% | Navigation bar, menus |
| 3 | 6dp | 11% | FABs, modal sheets, dialogs |
| 4 | 8dp | 12% | Elevated menus |
| 5 | 12dp | 14% | Modal navigation drawer |

## Component Conventions

### Buttons
- 40dp height, Full (pill) shape, Label Large text (14sp/500)
- **Filled**: container=`primary`, content=`onPrimary`
- **Filled Tonal**: container=`secondaryContainer`, content=`onSecondaryContainer`
- **Elevated**: container=`surfaceContainerLow`, content=`primary`, 1dp shadow
- **Outlined**: transparent + 1dp `outline` border, content=`primary`
- **Text**: transparent, content=`primary`
- Horizontal padding: 24dp (no icon), 16dp left + 24dp right (with icon)
- M3E sizes XS-XL with pill-shaped defaults and sentence case text

### FABs
- Container: `primaryContainer`, content: `onPrimaryContainer`, Level 3 elevation
- Small: 40x40dp (12dp shape), Standard: 56x56dp (16dp), Large: 96x96dp (28dp), Extended: 56dp height (16dp)
- Use FAB Menu for "New Drive" with sub-options (Quick Record, Replay Route, Import GPX)

### Cards
- Medium (12dp) shape, 16dp content padding
- **Elevated**: `surfaceContainerLow`, Level 1
- **Filled**: `surfaceContainerHighest`, Level 0
- **Outlined**: `surface`, Level 0, 1dp `outlineVariant` border

### Navigation
- **Navigation Bar**: 80dp height, 3-5 destinations, active pill 64x32dp `secondaryContainer`, 24dp icons, Label Medium
- **Navigation Rail**: 80dp width, optional FAB at top — use for medium/expanded width
- **Navigation Drawer**: 360dp width, active item uses `secondaryContainer` — Modal, Standard, Permanent variants
- Use `NavigationSuiteScaffold` to auto-select bar vs rail vs drawer based on window size

### Top App Bars
- Center-Aligned and Small: 64dp. Medium: collapses 112dp to 64dp. Large: 152dp to 64dp
- Container: `surface`, on-scroll tonal elevation tint
- M3E adds Search App Bar (taller, pill-shaped)

### Bottom Sheets
- Top corners Extra Large (28dp), drag handle 32x4dp centered, `onSurfaceVariant`
- Modal scrim blocks underlying content

### Dialogs
- Extra Large shape (28dp corners), `surfaceContainerHigh`, 280-560dp width, 24dp padding
- Title in Headline Small, body in Body Medium

### Chips
- 32dp height, Small shape (8dp), 1dp `outline` border
- Selected filter chips use `secondaryContainer`

## Adaptive Layout

### Window Size Classes

| Class | Width | Margins | Columns | Navigation |
|-------|-------|---------|---------|------------|
| Compact | <600dp | 16dp | 4 | Bottom bar |
| Medium | 600-839dp | 24dp | 8-12 | Navigation rail |
| Expanded | 840-1199dp | 24dp | 12 | Rail or drawer |
| Large | 1200-1599dp | 200dp+ | 12 | Permanent drawer |
| Extra-Large | >=1600dp | 200dp+ | 12 | Permanent drawer |

Use `currentWindowAdaptiveInfo().windowSizeClass` — classes reflect window size, not device type.

Gutters: 8dp on compact, 16dp+ on expanded. All spacing on a 4dp baseline grid.

### Canonical Layouts

- **List-Detail** (route management): side-by-side on expanded (>=840dp), single-pane stack on compact. Compose: `NavigableListDetailPaneScaffold`. Supports predictive back.
- **Supporting Pane** (map + stats): primary ~2/3, supporting ~1/3 on expanded. Supporting collapses to bottom sheet on compact. Compose: `NavigableSupportingPaneScaffold`.
- **Feed** (dashboard, activity history): `LazyVerticalGrid` with `GridCells.Adaptive(minSize)`. Compact: 1-2 columns, Medium: 2-3, Expanded: 3-4.

### Foldable Support

**Table-top posture** (horizontal fold, device half-opened) is the primary foldable use case — mount phone in car cradle:
- Top half: full map with GPX track, current position, speed overlay, GPS indicator
- Bottom half: large Start/Stop/Pause buttons (56dp+ touch targets), metric cards, pace note status
- No interactive elements across the fold/hinge

**Book posture** (vertical fold): left page = interactive map, right page = stats/elevation profile/pace note timeline.

Detect via `FoldingFeature.State.HALF_OPENED` + orientation check.

### Android Auto

Template-constrained via Car App Library (`androidx.car.app`). No custom drawing except map surfaces.

**Templates**: NavigationTemplate (track replay), MapWithContentTemplate (route browsing), TabTemplate (top-level nav), ListTemplate (garage/routes), GridTemplate (quick actions, max 6), PaneTemplate (activity summary).

**Safety constraints**:
- Maximum 6 taps to complete any task
- Screen stack depth: max 5 screens
- Touch targets: minimum 76dp width, 24dp spacing
- Font sizes: minimum 24sp
- Text truncation to 2 lines while driving
- No keyboard while driving (voice only)
- Action strip: maximum 4 actions
- No video, no complex animations

**Audio pace notes**: `MediaSession` integration, `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` to duck music. Turn-by-turn notifications via `NavigationManager.updateTrip()`.

**Day/Night mode**: host instructs theme switching — support both light and dark map themes.

### Wear OS

Design for round screens first (22% less UI space than square). Min design target: 192dp round. Most interactions under 5 seconds.

**Complications**: SHORT_TEXT (<=7 chars), RANGED_VALUE (0-100% dial), MONOCHROMATIC_IMAGE (single-color icon), LONG_TEXT (~20 chars).

**Tiles** (ProtoLayout, NOT Compose): Quick Record, Recent Drive, Today's Stats.

**Full app**: max 4 data fields on recording screen. Font sizes: 20-24sp primary, 14-16sp labels, 12sp minimum. Crown rotation scrolls between metric pages.

**Always-on display**: black background, white text, simplified metrics (time + distance only), no buttons. Burn-in protection shifts elements periodically. 1-minute refresh.

Key library: **Horologist** — `rememberResponsiveColumnState()`, `AmbientAware`.

## Activity Lifecycle

### Phase 1: Pre-Activity Planning
- Route preview cards: map thumbnail, elevation profile, surface type breakdown, difficulty rating, key stats
- Vehicle selection with fuel estimate
- Previous attempts: personal best, average time, completion count
- Difficulty colors: green (Easy), amber (Moderate), orange (Hard), red (Expert)

### Phase 2: During Activity (Live Recording)
- **Primary metrics** (always visible, largest): current speed, distance, elapsed time
- **Secondary metrics** (medium, glanceable): next pace note preview, surface type, GPS quality
- **Tertiary metrics** (small/on-demand): elevation, fuel remaining, average speed
- Customizable data field layouts: 2-6 fields per screen, multiple screens via swipe
- Auto-pause when stationary >30 seconds
- GPS quality indicator always visible (satellite count + accuracy radius)
- Audio-first: pace notes > safety warnings > metric updates > ambient audio

### Phase 3: Post-Activity Review
- Hero stats shown immediately: Total Distance, Total Time, Average Speed, Max Speed, Est. Fuel Used
- Save screen: name editor, speed-colored polyline map, vehicle assignment, route classification
- Detailed analysis: speed chart with pace note annotations, elevation profile, split analysis, map replay

### Phase 4: Historical Recall
- Dashboard trends: weekly/monthly/yearly aggregation with sparklines
- Multi-dimensional analytics: per-vehicle, per-route, per-period, cross-dimensional
- Calendar heatmap (GitHub-style): color intensity = distance driven
- Year-in-Review: narrative storyboard (December, ~6 weeks)

## Metrics Display Rules

A metric is **glanceable** when it communicates status in under 3 seconds: a single number with a progress bar, a trend arrow, a color-coded status, or a sparkline.

Information hierarchy: **Overview** (single metric + status indicator) -> **Summary** (4-6 metrics with mini-visualizations) -> **Detail** (full charts, split tables) -> **Raw Data** (GPX/CSV export).

Limit to 2 levels of progressive disclosure. Pattern: dashboard card summary -> activity detail page with expandable sections -> options menu for export/raw data.

## Data Visualization

- **Gradient polylines**: color-code GPX track by metric (speed, elevation, surface type)
- **Elevation profiles**: filled area charts with gradient coloring for grade steepness. Cross-reference: scrubbing highlights map position and vice versa
- **Calendar heatmaps**: sequential single-hue gradient (light to dark)
- **Ring/donut charts**: goal progress (60-100px wide, axis-free)
- **Sparklines**: inline with dashboard metric cards, 7-day or 30-day trend
- **Split tables**: per-section metrics, right-align numeric data, color-code fastest/slowest

## Driving-Safe UI

- OLED dark theme: true black (`#000000`) backgrounds, pure white sparingly for critical text only
- Desaturate accent colors slightly in dark mode (saturated colors vibrate on black)
- Single glance <=2 seconds, total task completion <=12 seconds cumulative glance time
- Minimum touch targets: 17.1mm (~68dp)
- One primary action per screen, core tasks in 1-2 taps maximum
- Physical button preference for start/stop recording (volume buttons or connected button)

## Navigation Destinations

Five bottom navigation items: **Dashboard**, **Record**, **Routes**, **Garage**, **Profile**.

Home screen uses Garmin-style customizable widget dashboard with a prominent recording FAB (Extended FAB with Split Button pattern).

## Notifications

Channels: `recording_active` (LOW), `pace_notes` (DEFAULT), `maintenance` (DEFAULT), `fuel_prompts` (DEFAULT), `activity_complete` (HIGH).

Ongoing recording notification (foreground service, `IMPORTANCE_LOW`, persistent).

## Code Conventions

- All screens are `@Composable` functions annotated with `@Destination` or navigated via type-safe `Screen` routes
- ViewModels use `@HiltViewModel` with constructor injection
- Database access through Room DAOs injected via Hilt modules in `di/`
- Preferences via DataStore (not SharedPreferences)
- Use `MaterialTheme.colorScheme.*` for standard M3 colors
- Use `MaterialTheme.rallyTraxColors.*` for domain-specific semantic colors
- Use `RallyTraxMotion.*` for spring animations (not duration-based)
- Use `RallyTraxTypeEmphasized.*` for accent text (hero numbers, important stats)
- Use `ShapeFullRound` for pill-shaped buttons and search bars
- Transition patterns: `SharedTransitionLayout` + `AnimatedContent` for container transforms, predictive back support
