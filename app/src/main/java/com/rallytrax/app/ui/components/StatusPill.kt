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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rallytrax.app.ui.theme.ShapeFullRound

enum class StatusPillSize { Sm, Md }

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    size: StatusPillSize = StatusPillSize.Md,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    leading: @Composable (() -> Unit)? = null,
) {
    val pad = when (size) {
        StatusPillSize.Sm -> PaddingValues(horizontal = 10.dp, vertical = 3.dp)
        StatusPillSize.Md -> PaddingValues(horizontal = 12.dp, vertical = 5.dp)
    }
    val fontSize = if (size == StatusPillSize.Sm) 11.sp else 12.sp
    Row(
        modifier = modifier.clip(ShapeFullRound).background(container).padding(pad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(
            text = text,
            color = contentColor,
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            ),
        )
    }
}
