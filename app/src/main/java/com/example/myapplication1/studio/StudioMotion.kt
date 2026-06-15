package com.example.myapplication1.studio

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Optional ambient drift applied to a scene's emitter when the user isn't steering it by
 * hand. It keeps the source gently *alive* — a fountain that sways, a portal that orbits —
 * so even an untouched scene feels animated rather than pinned. Returned as a small
 * [DpOffset] delta added on top of the (already smoothly-animated) base centre.
 */
sealed interface SceneMotion {
    val periodMs: Int

    data object None : SceneMotion {
        override val periodMs: Int get() = 1
    }

    /** Horizontal side-to-side glide. */
    data class Sway(val amplitudeDp: Float = 64f, override val periodMs: Int = 6500) : SceneMotion

    /** Vertical bob. */
    data class Bob(val amplitudeDp: Float = 42f, override val periodMs: Int = 5200) : SceneMotion

    /** Circular orbit. */
    data class Orbit(val radiusDp: Float = 46f, override val periodMs: Int = 9000) : SceneMotion

    /** Slow Lissajous wander (x and y on different periods) — feels organic. */
    data class Drift(val radiusDp: Float = 90f, override val periodMs: Int = 15000) : SceneMotion
}

private const val TWO_PI = (2.0 * PI).toFloat()

/**
 * The live ambient offset for [motion]. Returns [DpOffset.Zero] when there is no motion.
 * When [active] flips to false (the user starts dragging) the offset eases back to zero
 * over ~1.2s and eases back in on release — no snapping. Driven by one looping phase.
 */
@Composable
fun rememberAmbientOffset(motion: SceneMotion, active: Boolean): DpOffset {
    if (motion is SceneMotion.None) return DpOffset.Zero

    // Smooth hand-off so the living sway fades out while dragging and fades back in after.
    val gain by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 1200),
        label = "ambientGain",
    )
    if (gain <= 0.001f) return DpOffset.Zero

    val transition = rememberInfiniteTransition(label = "ambient")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = motion.periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    return when (motion) {
        is SceneMotion.None -> DpOffset.Zero
        is SceneMotion.Sway -> DpOffset((sin(phase) * motion.amplitudeDp * gain).dp, 0.dp)
        is SceneMotion.Bob -> DpOffset(0.dp, (sin(phase) * motion.amplitudeDp * gain).dp)
        is SceneMotion.Orbit ->
            DpOffset((cos(phase) * motion.radiusDp * gain).dp, (sin(phase) * motion.radiusDp * gain).dp)
        is SceneMotion.Drift -> DpOffset(
            (sin(phase) * motion.radiusDp * gain).dp,
            (sin(phase * 0.62f + 1.3f) * motion.radiusDp * 0.66f * gain).dp,
        )
    }
}
