package com.example.myapplication1.studio

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.piotrprus.particleemitter.ParticleShape

/** Auto-tour dwell time per scene. */
private const val TOUR_DWELL_MS = 8500L

/** Survives rotation: a nullable Float as itself, with NaN standing in for null. */
private val NullableFloatSaver = Saver<Float?, Float>(
    save = { it ?: Float.NaN },
    restore = { if (it.isNaN()) null else it },
)

/** Survives rotation: a nullable DpOffset packed into two floats (NaN x = null). */
private val NullableDpOffsetSaver = Saver<DpOffset?, FloatArray>(
    save = { o -> if (o == null) floatArrayOf(Float.NaN, 0f) else floatArrayOf(o.x.value, o.y.value) },
    restore = { a -> if (a[0].isNaN()) null else DpOffset(a[0].dp, a[1].dp) },
)

/**
 * Particle Studio — an immersive, full-screen showcase of `CanvasParticleEmitter`.
 *
 * This is the thin orchestrator: it owns the (rotation-surviving) state, detects
 * reduced-motion, drives the lifecycle-friendly auto-tour, and composes the
 * [StudioStage] (the live particle plane, isolated so it can recompose every frame
 * without dragging the chrome with it) under the Material 3 chrome.
 */
@Composable
fun ParticleStudioScreen() {
    val scenes = SceneCatalog.scenes
    var sceneIndex by rememberSaveable { mutableIntStateOf(0) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var autoTour by rememberSaveable { mutableStateOf(false) }

    // Live overrides — survive rotation/process death; reset only on a scene change.
    var intensity by rememberSaveable(stateSaver = NullableFloatSaver) { mutableStateOf<Float?>(null) }
    var gravityOn by rememberSaveable { mutableStateOf(true) }
    var edgeIndex by rememberSaveable { mutableIntStateOf(-1) }   // -1 = scene default
    var dragCenter by rememberSaveable(stateSaver = NullableDpOffsetSaver) { mutableStateOf<DpOffset?>(null) }

    // Reset is tied to the scene-CHANGE action (selector tap / auto-tour tick), NOT to a
    // LaunchedEffect(sceneIndex) — otherwise an Activity recreation (rotation) would re-fire
    // it and clobber the rememberSaveable-restored overrides.
    fun resetOverrides() {
        intensity = null
        gravityOn = true
        edgeIndex = -1
        dragCenter = null
    }

    fun goToScene(index: Int) {
        if (index != sceneIndex) resetOverrides()
        sceneIndex = index
    }

    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    // Auto-tour driven by the frame clock, so it parks when the app is backgrounded.
    LaunchedEffect(autoTour, scenes.size) {
        if (!autoTour) return@LaunchedEffect
        val dwellNanos = TOUR_DWELL_MS * 1_000_000
        var acc = 0L
        var prev = 0L
        while (true) {
            withFrameNanos { t ->
                if (prev != 0L) acc += t - prev
                prev = t
            }
            if (acc >= dwellNanos) {
                acc = 0L
                goToScene((sceneIndex + 1) % scenes.size)
            }
        }
    }

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val assets = remember(density) { StudioAssets(density) }
    val scene = scenes[sceneIndex]
    val shapes = remember(scene.id, density, measurer, assets) { resolveShapes(scene, density, measurer, assets) }
    val backdropGlowShapes = remember(assets) { listOf<ParticleShape>(ParticleShape.Image(assets.glow)) }

    val accent by animateColorAsState(scene.accent, tween(if (reduceMotion) 0 else 600), label = "accent")
    val targetPps = intensity ?: scene.particlePerSecond.toFloat()
    val edge = if (edgeIndex >= 0) EDGE_OPTIONS[edgeIndex].behavior else scene.edge

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A docked side panel needs BOTH room across (≥840dp: tablet / foldable / unfolded)
        // AND room down (≥600dp) — a short phone-landscape would just cramp it, so that case
        // keeps the immersive top-bar + centered-bottom-controls column.
        val expanded = maxWidth >= 840.dp && maxHeight >= 600.dp

        StudioStage(
            scene = scene,
            shapes = shapes,
            backdropGlowShapes = backdropGlowShapes,
            dragCenter = dragCenter,
            onSteer = { autoTour = false; dragCenter = it },
            targetPps = targetPps,
            gravityOn = gravityOn,
            edge = edge,
            accent = accent,
            sceneIndex = sceneIndex,
            reduceMotion = reduceMotion,
        )

        val topBar: @Composable (Modifier) -> Unit = { m ->
            StudioTopBar(
                scene = scene,
                accent = accent,
                index = sceneIndex + 1,
                total = scenes.size,
                infoOpen = showInfo,
                onToggleInfo = { showInfo = !showInfo },
                autoTour = autoTour,
                onToggleTour = { autoTour = !autoTour },
                reduceMotion = reduceMotion,
                modifier = m,
            )
        }
        val controls: @Composable (Modifier) -> Unit = { m ->
            LiveControlsPanel(
                accent = accent,
                pps = targetPps,
                onPps = { intensity = it },
                gravityOn = gravityOn,
                onGravity = { gravityOn = it },
                currentEdge = edge,
                onEdge = { edgeIndex = edgeIndexOf(it) },
                onReset = ::resetOverrides,
                modifier = m,
            )
        }

        if (expanded) {
            // Side panel over a full-bleed stage; the scene list runs vertically and scrolls.
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .width(360.dp)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
                ) {
                    topBar(Modifier)
                    AnimatedVisibility(
                        visible = showInfo,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        SceneInfoPanel(scene, accent, Modifier.padding(top = 10.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    controls(Modifier)
                    Spacer(Modifier.height(14.dp))
                    SceneSelectorColumn(
                        scenes = scenes,
                        accent = accent,
                        selected = sceneIndex,
                        onSelect = { autoTour = false; goToScene(it) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                topBar(
                    Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )

                AnimatedVisibility(
                    visible = showInfo,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    SceneInfoPanel(scene, accent, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }

                Spacer(Modifier.weight(1f))

                Column(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    controls(Modifier.padding(horizontal = 16.dp))
                    SceneSelectorRail(
                        scenes = scenes,
                        accent = accent,
                        selected = sceneIndex,
                        onSelect = { autoTour = false; goToScene(it) },
                        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}
