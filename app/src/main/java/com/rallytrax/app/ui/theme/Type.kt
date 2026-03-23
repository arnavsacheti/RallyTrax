package com.rallytrax.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val RallyTraxTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 57.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontSize = 45.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontSize = 36.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

// ── M3 Expressive: Emphasized typography variants ──────────────────────────

object RallyTraxTypeEmphasized {
    val displayLarge = TextStyle(
        fontSize = 57.sp, fontWeight = FontWeight.Medium, lineHeight = 64.sp, letterSpacing = (-0.25).sp,
    )
    val displayMedium = TextStyle(
        fontSize = 45.sp, fontWeight = FontWeight.Medium, lineHeight = 52.sp, letterSpacing = 0.sp,
    )
    val displaySmall = TextStyle(
        fontSize = 36.sp, fontWeight = FontWeight.Medium, lineHeight = 44.sp, letterSpacing = 0.sp,
    )
    val headlineLarge = TextStyle(
        fontSize = 32.sp, fontWeight = FontWeight.Medium, lineHeight = 40.sp, letterSpacing = 0.sp,
    )
    val headlineMedium = TextStyle(
        fontSize = 28.sp, fontWeight = FontWeight.Medium, lineHeight = 36.sp, letterSpacing = 0.sp,
    )
    val headlineSmall = TextStyle(
        fontSize = 24.sp, fontWeight = FontWeight.Medium, lineHeight = 32.sp, letterSpacing = 0.sp,
    )
    val titleLarge = TextStyle(
        fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp, letterSpacing = 0.sp,
    )
    val titleMedium = TextStyle(
        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp, letterSpacing = 0.15.sp,
    )
    val titleSmall = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    )
    val bodyLarge = TextStyle(
        fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp, letterSpacing = 0.5.sp,
    )
    val bodyMedium = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.25.sp,
    )
    val bodySmall = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    )
    val labelLarge = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    )
    val labelMedium = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    )
    val labelSmall = TextStyle(
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    )
}
