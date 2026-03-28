package com.rallytrax.app.ui.components

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rallytrax.app.ui.theme.LocalRuggedMode

/**
 * Applies a minimum 68dp touch target when rugged mode is active.
 */
@Composable
fun Modifier.ruggedTouchTarget(): Modifier {
    return if (LocalRuggedMode.current) {
        this.sizeIn(minWidth = 68.dp, minHeight = 68.dp)
    } else {
        this
    }
}

/**
 * Returns increased padding when rugged mode is active.
 */
@Composable
fun ruggedPadding(): Dp = if (LocalRuggedMode.current) 20.dp else 16.dp
