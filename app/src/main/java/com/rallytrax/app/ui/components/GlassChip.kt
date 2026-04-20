package com.rallytrax.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rallytrax.app.ui.theme.LocalRallyTraxColors
import com.rallytrax.app.ui.theme.ShapeFullRound

/** Semi-transparent pill for overlaying on map hero images / gradient backgrounds. */
@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
) {
    val rt = LocalRallyTraxColors.current
    Row(
        modifier = modifier
            .clip(ShapeFullRound)
            .background(rt.glassChip)
            .padding(PaddingValues(horizontal = 10.dp, vertical = 4.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(
            text = text,
            color = rt.onGlassChip,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            ),
        )
    }
}
