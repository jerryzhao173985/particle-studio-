package com.example.myapplication1.studio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Semantic haptics for the studio's controls, using Compose's built-in [HapticFeedback]
 * (no VIBRATE permission, no dependency). Each interaction maps to the most fitting
 * [HapticFeedbackType] so the app *feels* native. Disabled under reduced-motion.
 */
class StudioHaptics(private val haptics: HapticFeedback, private val enabled: Boolean) {
    fun burst() = play(HapticFeedbackType.Confirm)
    fun toggle(on: Boolean) = play(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
    fun select() = play(HapticFeedbackType.SegmentTick)
    fun reset() = play(HapticFeedbackType.Reject)
    fun steerStart() = play(HapticFeedbackType.GestureThresholdActivate)

    private fun play(type: HapticFeedbackType) {
        if (enabled) haptics.performHapticFeedback(type)
    }
}

@Composable
fun rememberStudioHaptics(enabled: Boolean = true): StudioHaptics {
    val haptics = LocalHapticFeedback.current
    return remember(haptics, enabled) { StudioHaptics(haptics, enabled) }
}
