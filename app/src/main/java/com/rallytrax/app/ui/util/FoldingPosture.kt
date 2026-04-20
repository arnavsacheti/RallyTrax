package com.rallytrax.app.ui.util

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

enum class FoldingPosture {
    Flat,       // Normal phone/tablet, no fold or fully open
    TableTop,   // Horizontal hinge, half-opened — top half sees map, bottom half controls
    Book,       // Vertical hinge, half-opened — left pane + right pane
}

@Composable
fun rememberFoldingPosture(): State<FoldingPosture> {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    return produceState(initialValue = FoldingPosture.Flat, key1 = activity) {
        val currentActivity = activity ?: return@produceState
        WindowInfoTracker.getOrCreate(currentActivity)
            .windowLayoutInfo(currentActivity)
            .map { layoutInfo ->
                val fold = layoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()
                    ?: return@map FoldingPosture.Flat
                if (fold.state != FoldingFeature.State.HALF_OPENED) {
                    FoldingPosture.Flat
                } else {
                    when (fold.orientation) {
                        FoldingFeature.Orientation.HORIZONTAL -> FoldingPosture.TableTop
                        FoldingFeature.Orientation.VERTICAL -> FoldingPosture.Book
                        else -> FoldingPosture.Flat
                    }
                }
            }
            .filterNotNull()
            .collect { value = it }
    }
}
