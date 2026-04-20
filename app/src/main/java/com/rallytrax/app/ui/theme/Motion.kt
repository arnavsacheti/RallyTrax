package com.rallytrax.app.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

// ── M3 Expressive: Spring-based motion tokens ──────────────────────────────

object RallyTraxMotion {
    // Small components: switches, buttons, icon toggles
    fun <T> fastSpatial(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)
    fun <T> fastEffects(): SpringSpec<T> = spring(dampingRatio = 1.0f, stiffness = 3800f)

    // Partial-screen: sheets, drawers, expanding cards
    fun <T> defaultSpatial(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)
    fun <T> defaultEffects(): SpringSpec<T> = spring(dampingRatio = 1.0f, stiffness = 1600f)

    // Full-screen: page transitions, full-screen modals
    fun <T> slowSpatial(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 300f)
    fun <T> slowEffects(): SpringSpec<T> = spring(dampingRatio = 1.0f, stiffness = 800f)

    // Signature expressive easing — matches prototype's cubic-bezier(0.2, 0.9, 0.2, 1.05).
    // Slight overshoot for staggered-enter animations (FAB menu mini-fabs, peek sheets, chips).
    fun <T> expressive(): SpringSpec<T> = spring(dampingRatio = 0.62f, stiffness = 500f)
    fun <T> expressiveSnappy(): SpringSpec<T> = spring(dampingRatio = 0.7f, stiffness = 900f)
}
