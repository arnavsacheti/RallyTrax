package com.rallytrax.app.ui.yearinreview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rallytrax.app.data.preferences.UnitSystem
import com.rallytrax.app.ui.theme.RallyTraxTypeEmphasized
import com.rallytrax.app.util.formatDistance
import com.rallytrax.app.util.formatElapsedTime
import com.rallytrax.app.util.formatElevation
import com.rallytrax.app.util.formatSpeed
import com.rallytrax.app.util.speedUnit
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearInReviewScreen(
    onBack: () -> Unit,
    viewModel: YearInReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = state.preferences.unitSystem

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${state.year} in Review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = Color.Black,
    ) { innerPadding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            state.trackCount == 0 -> EmptyState(modifier = Modifier.padding(innerPadding))
            else -> StoryPager(
                state = state,
                unit = unit,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun StoryPager(
    state: YearInReviewState,
    unit: UnitSystem,
    modifier: Modifier = Modifier,
) {
    val pages = remember(state) { buildPages(state, unit) }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(0.dp),
        ) { index ->
            StoryPage(pages[index])
        }

        // Page indicator dots at the top, clear of the app bar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(pages.size) { i ->
                Box(
                    modifier = Modifier
                        .size(width = if (i == pagerState.currentPage) 24.dp else 8.dp, height = 4.dp)
                        .padding(horizontal = 2.dp)
                        .background(
                            color = if (i == pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.35f),
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun StoryPage(page: YearInReviewPage) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(page.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = page.label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = page.headline,
                fontSize = page.headlineSp.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = (page.headlineSp * 1.05f).sp,
            )
            if (page.subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = page.subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Route,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = "No drives yet this year",
                style = RallyTraxTypeEmphasized.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Record a drive and check back — your year in review will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Page model ──────────────────────────────────────────────────────────────

private data class YearInReviewPage(
    val icon: ImageVector,
    val label: String,
    val headline: String,
    val subtitle: String?,
    val background: Brush,
    val headlineSp: Float = 64f,
)

private fun buildPages(state: YearInReviewState, unit: UnitSystem): List<YearInReviewPage> {
    val gradients = listOf(
        Brush.verticalGradient(listOf(Color(0xFF1A73E8), Color(0xFF0B2545))),
        Brush.verticalGradient(listOf(Color(0xFF34A853), Color(0xFF0D3220))),
        Brush.verticalGradient(listOf(Color(0xFFFBBC04), Color(0xFF6B4A00))),
        Brush.verticalGradient(listOf(Color(0xFFEA4335), Color(0xFF5C1311))),
        Brush.verticalGradient(listOf(Color(0xFF9334E6), Color(0xFF2E1250))),
        Brush.verticalGradient(listOf(Color(0xFFE91E63), Color(0xFF4A0723))),
        Brush.verticalGradient(listOf(Color(0xFF1A73E8), Color(0xFF0B2545))),
    )

    val pages = mutableListOf<YearInReviewPage>()
    pages += YearInReviewPage(
        icon = Icons.Filled.Route,
        label = "${state.year}",
        headline = "${state.trackCount}",
        subtitle = if (state.trackCount == 1) "drive logged this year" else "drives logged this year",
        background = gradients[0],
    )
    pages += YearInReviewPage(
        icon = Icons.Filled.Route,
        label = "Distance",
        headline = formatDistance(state.totalDistanceMeters, unit),
        subtitle = "covered across every drive",
        background = gradients[1],
    )
    pages += YearInReviewPage(
        icon = Icons.Filled.Timer,
        label = "Time behind the wheel",
        headline = formatElapsedTime(state.totalDurationMs),
        subtitle = "over ${state.activeDays} active day${if (state.activeDays == 1) "" else "s"}",
        background = gradients[2],
    )
    pages += YearInReviewPage(
        icon = Icons.Filled.Speed,
        label = "Top speed",
        headline = "${formatSpeed(state.maxSpeedMps, unit)} ${speedUnit(unit)}",
        subtitle = state.fastestTrackName?.let { "on $it" } ?: "max recorded this year",
        background = gradients[3],
        headlineSp = 56f,
    )
    if (state.longestTrackName != null) {
        pages += YearInReviewPage(
            icon = Icons.Filled.EmojiEvents,
            label = "Longest drive",
            headline = formatDistance(state.longestTrackDistanceMeters, unit),
            subtitle = state.longestTrackName,
            background = gradients[4],
            headlineSp = 56f,
        )
    }
    if (state.totalElevationGainM > 0) {
        pages += YearInReviewPage(
            icon = Icons.Filled.Landscape,
            label = "Elevation climbed",
            headline = formatElevation(state.totalElevationGainM, unit),
            subtitle = "total vertical gain",
            background = gradients[5],
            headlineSp = 56f,
        )
    }
    pages += YearInReviewPage(
        icon = Icons.Filled.CalendarMonth,
        label = "Here's to next year",
        headline = "Thanks for riding with RallyTrax",
        subtitle = null,
        background = gradients[6],
        headlineSp = 36f,
    )
    return pages
}
