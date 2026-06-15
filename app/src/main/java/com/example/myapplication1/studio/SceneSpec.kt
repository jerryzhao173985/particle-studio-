package com.example.myapplication1.studio

import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.piotrprus.particleemitter.CanvasEmitterConfig
import dev.piotrprus.particleemitter.EdgeBehavior

/**
 * Which concrete [dev.piotrprus.particleemitter.ParticleShape] a scene draws.
 * Resolved to real shape instances (which need a density / TextMeasurer / bitmaps)
 * in [resolveShapes].
 */
enum class ShapeKind {
    CIRCLE,
    GLOW_IMAGE,      // soft round bokeh sprite
    VEIL_IMAGE,      // tall soft column — for aurora curtains
    STREAK_IMAGE,    // elongated streak — rain (vertical) / meteor (horizontal)
    STAR_PATH,
    HEART_PATH,
    TEXT_EMOJI,
}

/**
 * A symbolic emitter position. Mapped to a concrete [DpOffset] from the live screen
 * size by [position] so scenes are resolution-independent.
 */
enum class Anchor {
    TOP_LEFT, TOP_FULL_WIDTH,
    CENTER,
    BOTTOM_CENTER, BOTTOM_FULL_WIDTH,
    LEFT_CENTER;

    fun position(w: Dp, h: Dp): DpOffset = when (this) {
        TOP_LEFT -> DpOffset(w * 0.04f, h * 0.05f)
        TOP_FULL_WIDTH -> DpOffset(w / 2, 0.dp)
        CENTER -> DpOffset(w / 2, h / 2)
        BOTTOM_CENTER -> DpOffset(w / 2, h * 0.90f)
        BOTTOM_FULL_WIDTH -> DpOffset(w / 2, h * 0.96f)
        LEFT_CENTER -> DpOffset(0.dp, h / 2)
    }
}

/**
 * An immutable, fully-specified particle scene. Holds Compose-native types directly
 * (no enum→type mapping needed) so it converts to a [CanvasEmitterConfig] in one step
 * via [toConfig]. Fields that are usually their default (region dims, rotation, gravity
 * angle, motion) carry defaults so each catalog row reads as its *meaningful* values.
 *
 * Physics conventions (see [CanvasEmitterConfig]): force-angle 0° = up, gravity-angle
 * 0° = down, integer scale ranges (grow start<target; shrink target 0..0).
 */
data class SceneSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val features: List<String>,
    val particlePerSecond: Int,
    val anchor: Anchor,
    val regionShape: CanvasEmitterConfig.Shape,
    val shapeKinds: List<ShapeKind>,
    val lifespan: IntRange,
    val fadeOut: IntRange,
    val scaleTime: IntRange,
    val colors: List<Color>,
    val sizes: List<DpSize>,
    val spread: IntRange,
    val blend: BlendMode,
    val initialForce: IntRange,
    val startScale: IntRange,
    val targetScale: IntRange,
    val gravityStrength: Float,
    val edge: EdgeBehavior,
    val alphaEasing: Easing,
    val scaleEasing: Easing,
    /** Three vertical-gradient stops for the scene backdrop (top → bottom). */
    val gradient: List<Color>,
    /** Vivid accent used for active UI affordances; echoes the scene's particle palette. */
    val accent: Color,
    // --- usually-default fields ---
    val regionWidthDp: Float = 0f,
    val regionHeightDp: Float = 0f,
    /** When true the start region spans the full screen width (e.g. a top H_LINE band). */
    val fullWidthRegion: Boolean = false,
    /** When true the start region spans the full screen height (e.g. a side V_LINE). */
    val fullHeightRegion: Boolean = false,
    val rotation: IntRange = 0..0,
    val gravityAngle: Int = 0,
    val hideInStartRegion: Boolean = false,
    val emojis: List<String> = emptyList(),
    /** Ambient drift applied to the emitter when the user isn't steering it. */
    val motion: SceneMotion = SceneMotion.None,
) {
    /** True when the scene rotates its (non-circle) particles — used to orient streaks. */
    val rotates: Boolean get() = rotation.first != 0 || rotation.last != 0

    private val lineWidthFix: Boolean
        get() = fullWidthRegion && regionShape == CanvasEmitterConfig.Shape.H_LINE
    private val lineHeightFix: Boolean
        get() = fullHeightRegion && regionShape == CanvasEmitterConfig.Shape.V_LINE

    /**
     * The engine spawns a line region across `[c - size/2, c + size]` (asymmetric — a known
     * v1.1.0 quirk). For a full-span line we therefore use 2/3 of the screen extent so the
     * 1.5× spawn range lands exactly across `[0, full]`, and shift the centre in [defaultCenter].
     */
    fun regionSize(w: Dp, h: Dp): DpSize = DpSize(
        width = when {
            lineWidthFix -> w * 2 / 3
            fullWidthRegion -> w
            else -> regionWidthDp.dp
        },
        height = when {
            lineHeightFix -> h * 2 / 3
            fullHeightRegion -> h
            else -> regionHeightDp.dp
        },
    )

    /** The scene's default emitter centre, corrected for the asymmetric line-spawn range. */
    fun defaultCenter(w: Dp, h: Dp): DpOffset {
        val base = anchor.position(w, h)
        return DpOffset(
            x = if (lineWidthFix) w / 3 else base.x,
            y = if (lineHeightFix) h / 3 else base.y,
        )
    }
}
