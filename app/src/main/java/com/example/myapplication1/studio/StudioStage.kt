package com.example.myapplication1.studio

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import dev.piotrprus.particleemitter.CanvasParticleEmitter
import dev.piotrprus.particleemitter.EdgeBehavior
import dev.piotrprus.particleemitter.ParticleShape
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Drag-velocity "comet kick": a fast fling briefly spikes the birth-rate, then decays. */
private const val FLING_BOOST = 120f   // extra particles/sec at a full-speed fling
private const val FLING_REF = 55f      // px-per-drag-event that counts as "full speed"

/**
 * The live particle stage, isolated from the chrome so the chrome doesn't recompose every
 * frame. Layered back-to-front: living gradient + colour wash + accent spotlight (drawn) →
 * parallax dust emitter → main emitter → vignette + scene-flash + tap shockwave (drawn) →
 * gesture surface. All the per-frame animation is read at *draw time* (drawBehind) except
 * the emitter centre, which must be a config value.
 */
@Composable
internal fun StudioStage(
    scene: SceneSpec,
    shapes: List<ParticleShape>,
    backdropGlowShapes: List<ParticleShape>,
    dragCenter: DpOffset?,
    onSteer: (DpOffset) -> Unit,
    targetPps: Float,
    gravityOn: Boolean,
    edge: EdgeBehavior,
    accent: Color,
    sceneIndex: Int,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberStudioHaptics(enabled = !reduceMotion)
    val tilt by rememberTiltOffset(enabled = !reduceMotion)

    // Transient tap burst — does NOT latch the steering position, so ambient motion resumes.
    val burst = remember { Animatable(0f) }
    var burstCenter by remember { mutableStateOf<DpOffset?>(null) }
    // Drag-velocity kick — a fast fling throws a comet of extra particles, then decays.
    val fling = remember { Animatable(0f) }

    // Scene-change flash.
    val flash = remember { Animatable(0f) }
    LaunchedEffect(sceneIndex) {
        if (!reduceMotion) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
        }
    }

    // One infinite clock for the living wash + glow pulse (read only at draw time).
    val clock = rememberInfiniteTransition(label = "stageClock")
    val washPhase = clock.animateFloat(
        0f, 1f, infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse), label = "wash",
    )
    val pulse = clock.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight

        val base = dragCenter ?: scene.defaultCenter(w, h)
        val animX by animateDpAsState(base.x, label = "cx")
        val animY by animateDpAsState(base.y, label = "cy")
        val ambient = rememberAmbientOffset(
            motion = if (reduceMotion) SceneMotion.None else scene.motion,
            active = dragCenter == null,
        )
        var emitX = animX + ambient.x
        var emitY = animY + ambient.y
        // During a tap burst, pull the source toward the tap then ease back — never latches.
        val bcenter = burstCenter
        if (bcenter != null && dragCenter == null && burst.value > 0.01f) {
            emitX = lerp(emitX, bcenter.x, burst.value)
            emitY = lerp(emitY, bcenter.y, burst.value)
        }

        val animPps by animateFloatAsState(targetPps, tween(700), label = "pps")
        // Breathing: the whole field gently swells and ebbs on the shared pulse clock
        // (~±16%). Reading pulse here re-runs only this isolated stage body, not the chrome.
        val breath = if (reduceMotion) 1f else 1f + 0.16f * (pulse.value - 0.5f) * 2f
        val livePps = (animPps * breath + burst.value * BURST_BOOST + fling.value * FLING_BOOST).roundToInt()
        val animGravity by animateFloatAsState(
            if (gravityOn) scene.gravityStrength else 0f, tween(500), label = "gravity",
        )

        val mainConfig = scene.toConfig(
            center = DpOffset(emitX, emitY),
            regionSize = scene.regionSize(w, h),
            shapes = shapes,
            particlePerSecond = livePps,
            gravityStrength = animGravity,
            edge = edge,
        )
        // Parallax: the dust lags the steered source (moves ~⅓ as far) so dragging shears the
        // field through depth — the main scene tracks 1:1, the backdrop trails behind it.
        val anchorC = scene.defaultCenter(w, h)
        // Parallax also responds to device tilt (±30dp) so depth shifts as you tilt the phone.
        val backCenter = DpOffset(
            w / 2 + (emitX - anchorC.x) * 0.32f + (tilt.x * 30f).dp,
            h / 2 + (emitY - anchorC.y) * 0.32f + (tilt.y * 30f).dp,
        )
        // Keep the backdrop emitter mounted (no add/remove churn on switch), but zero its
        // birth-rate on opaque "paper" scenes so no additive dust hazes behind solid shapes.
        val backdropPps = if (scene.backdrop) (animPps * 0.18f).roundToInt().coerceIn(4, 14) else 0
        val backdropConfig = scene.toBackdropConfig(
            center = backCenter,
            regionSize = DpSize(w, h),
            glowShapes = backdropGlowShapes,
            particlePerSecond = backdropPps,
        )

        // Normalised intensity for the glow pulse.
        val intensity = ((targetPps - MIN_PPS) / (MAX_PPS - MIN_PPS)).coerceIn(0f, 1f)

        val g0 by animateColorAsState(scene.gradient[0], tween(if (reduceMotion) 0 else 800), label = "g0")
        val g1 by animateColorAsState(scene.gradient[1], tween(if (reduceMotion) 0 else 800), label = "g1")
        val g2 by animateColorAsState(scene.gradient[2], tween(if (reduceMotion) 0 else 800), label = "g2")

        // --- Layer A: living gradient + colour wash + accent spotlight (draw-time reads) ---
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(g0, g1, g2)))

                    // Slowly drifting off-axis colour wash → the sky is never flat.
                    val wp = if (reduceMotion) 0.5f else washPhase.value
                    val washX = size.width * (0.30f + 0.16f * (wp - 0.5f) * 2f)
                    val washY = size.height * (0.26f + 0.10f * (0.5f - wp) * 2f)
                    drawRect(
                        Brush.radialGradient(
                            listOf(g1.copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(washX, washY),
                            radius = size.maxDimension * 0.62f,
                        )
                    )

                    // Accent spotlight following the emitter; brightness tracks intensity + pulse.
                    val pulseAmt = if (reduceMotion) 0f else (pulse.value - 0.5f) * 0.06f
                    val spotAlpha = (0.06f + 0.16f * intensity + pulseAmt).coerceIn(0.04f, 0.26f)
                    drawRect(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = spotAlpha), Color.Transparent),
                            center = Offset(emitX.toPx(), emitY.toPx()),
                            radius = 340.dp.toPx() * (1f + 0.10f * intensity),
                        )
                    )
                }
        )

        // --- Layer A2: the scene's GPU AGSL atmosphere, blended additively over the gradient
        // (per-scene & typed; API 33+, else no-op — None scenes keep the plain gradient) ---
        AtmosphereLayer(
            atmosphere = scene.atmosphere,
            accent = accent,
            intensity = intensity,
            touchPx = with(density) { Offset(emitX.toPx(), emitY.toPx()) },
            reduceMotion = reduceMotion,
        )

        // --- Layer B: parallax dust behind the main scene ---
        CanvasParticleEmitter(modifier = Modifier.fillMaxSize(), config = backdropConfig)

        // --- Layer C: the main scene ---
        CanvasParticleEmitter(modifier = Modifier.fillMaxSize(), config = mainConfig)

        // --- Layer C2: additive bloom — a blurred Plus echo that makes luminous scenes glow.
        // ONLY on pre-API-33 devices: on API 33+ the GPU AGSL aurora shader already supplies the
        // glow, so running a whole extra CPU particle simulation + full-screen blur here is pure
        // redundant main-thread cost (the dominant cause of jank measured on-device). Gating it to
        // !agslSupported removes a third emitter on modern phones for ~zero visual loss.
        if (scene.additive && !reduceMotion && !agslSupported) {
            val bloomConfig = mainConfig.copy(
                particlePerSecond = (livePps * 0.42f).roundToInt().coerceAtMost(40),
                blendMode = BlendMode.Plus,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .blur(14.dp)
                    .graphicsLayer { alpha = 0.5f }
            ) {
                CanvasParticleEmitter(modifier = Modifier.fillMaxSize(), config = bloomConfig)
            }
        }

        // --- Layer D: vignette + scene flash + tap shockwave (draw-time reads) ---
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            0f to Color.Transparent,
                            0.58f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.60f),
                            radius = size.maxDimension * 0.86f,
                        )
                    )

                    val fv = flash.value
                    if (fv > 0.01f) {
                        val grow = 1f - fv
                        val r = size.minDimension * 0.18f + size.maxDimension * 0.55f * grow
                        drawCircle(
                            color = accent.copy(alpha = 0.22f * fv),
                            radius = r,
                            center = center,
                            style = Stroke(width = (2.5f.dp.toPx()) * (0.4f + fv)),
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color.Transparent, accent.copy(alpha = 0.10f * fv), Color.Transparent),
                                center = center,
                                radius = r,
                            ),
                            radius = r,
                            center = center,
                        )
                    }

                    val bv = burst.value
                    val bc = burstCenter
                    if (bv > 0.01f && bc != null) {
                        val grow = 1f - bv
                        val r = 24.dp.toPx() + 180.dp.toPx() * grow
                        drawCircle(
                            color = accent.copy(alpha = 0.45f * bv),
                            radius = r,
                            center = Offset(bc.x.toPx(), bc.y.toPx()),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
        )

        // --- Layer E: gesture surface (tap = burst, drag = steer) ---
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        // Tap-to-place: touching a spot moves the source THERE and keeps emitting
                        // (it glides over and stays), then puffs a burst — instead of snapping back.
                        val p = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
                        onSteer(p)
                        burstCenter = p
                        haptics.burst()
                        scope.launch {
                            burst.snapTo(1f)
                            burst.animateTo(0f, tween(750))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            haptics.steerStart()
                            onSteer(with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) })
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onSteer(with(density) { DpOffset(change.position.x.toDp(), change.position.y.toDp()) })
                            // Fling velocity → a decaying comet kick on the birth-rate. Only
                            // re-trigger when the new speed exceeds the current (decaying) level,
                            // so a steady drag doesn't thrash the animation.
                            val kick = (dragAmount.getDistance() / FLING_REF).coerceIn(0f, 1f)
                            if (kick > fling.value) scope.launch {
                                fling.snapTo(kick)
                                fling.animateTo(0f, tween(600))
                            }
                        },
                    )
                }
        )
    }
}
