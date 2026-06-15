package com.example.myapplication1.studio

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.BlendMode
import dev.piotrprus.particleemitter.CanvasEmitterConfig
import dev.piotrprus.particleemitter.EdgeBehavior
import dev.piotrprus.particleemitter.ParticleShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Procedurally-generated particle artwork. Generated once and reused — never allocate
 * a Bitmap or Path per frame.
 *
 * The library's Image renderer tints the bitmap with `ColorFilter.tint(particle.color)`,
 * so every sprite here is drawn in WHITE with an alpha falloff; the per-particle colour
 * comes from the scene palette at draw time.
 */

/** Shared "mid glow" alpha (0..255) for the soft falloff of the procedural sprites. */
private const val MID_GLOW_ALPHA = 205

/** A soft round bokeh sprite: opaque white core fading to transparent at the rim. */
internal fun softGlowBitmap(sizePx: Int): ImageBitmap {
    val size = sizePx.coerceAtLeast(4)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val r = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            r, r, r,
            intArrayOf(
                AndroidColor.WHITE,
                AndroidColor.argb(MID_GLOW_ALPHA, 255, 255, 255),
                AndroidColor.argb(55, 255, 255, 255),
                AndroidColor.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.32f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawCircle(r, r, r, paint)
    return bmp.asImageBitmap()
}

/** An elongated capsule of glow — a streak. Long axis is vertical or horizontal. */
internal fun streakBitmap(longPx: Int, shortPx: Int, vertical: Boolean): ImageBitmap {
    val w = (if (vertical) shortPx else longPx).coerceAtLeast(3)
    val h = (if (vertical) longPx else shortPx).coerceAtLeast(3)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = if (vertical) {
            LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(AndroidColor.TRANSPARENT, AndroidColor.WHITE, AndroidColor.argb(MID_GLOW_ALPHA, 255, 255, 255), AndroidColor.TRANSPARENT),
                floatArrayOf(0f, 0.42f, 0.72f, 1f), Shader.TileMode.CLAMP,
            )
        } else {
            LinearGradient(
                0f, 0f, w.toFloat(), 0f,
                intArrayOf(AndroidColor.TRANSPARENT, AndroidColor.argb(MID_GLOW_ALPHA, 255, 255, 255), AndroidColor.WHITE, AndroidColor.TRANSPARENT),
                floatArrayOf(0f, 0.28f, 0.58f, 1f), Shader.TileMode.CLAMP,
            )
        }
    }
    val radius = (if (vertical) w else h) / 2f
    canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, paint)
    return bmp.asImageBitmap()
}

/** A tall, soft vertical column of glow — for aurora-curtain particles. */
internal fun veilBitmap(wPx: Int, hPx: Int): ImageBitmap {
    val w = wPx.coerceAtLeast(8)
    val h = hPx.coerceAtLeast(8)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val cx = w / 2f
    val cy = h / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            cx, cy, w / 2f,
            intArrayOf(
                AndroidColor.argb(MID_GLOW_ALPHA, 255, 255, 255),
                AndroidColor.argb(90, 255, 255, 255),
                AndroidColor.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
    }
    canvas.save()
    canvas.scale(1f, h.toFloat() / w.toFloat(), cx, cy) // stretch the soft circle into a tall ellipse
    canvas.drawCircle(cx, cy, w / 2f, paint)
    canvas.restore()
    return bmp.asImageBitmap()
}

/** A five-pointed star [Path], centred on the origin. */
internal fun starPath(radiusPx: Float, points: Int = 5, innerRatio: Float = 0.44f): Path {
    val path = Path()
    val outer = radiusPx
    val inner = radiusPx * innerRatio
    for (i in 0 until points * 2) {
        val rad = if (i % 2 == 0) outer else inner
        val a = (PI / points) * i - PI / 2.0
        val x = (rad * cos(a)).toFloat()
        val y = (rad * sin(a)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** A heart [Path], centred (approximately) on the origin. */
internal fun heartPath(radiusPx: Float): Path {
    val s = radiusPx
    val path = Path()
    path.moveTo(0f, -0.30f * s)
    path.cubicTo(-0.55f * s, -1.05f * s, -1.30f * s, -0.10f * s, 0f, 0.85f * s)
    path.cubicTo(1.30f * s, -0.10f * s, 0.55f * s, -1.05f * s, 0f, -0.30f * s)
    path.close()
    return path
}

/**
 * Holds the shared, procedurally-built sprites for one density. Built once and remembered.
 */
class StudioAssets(density: Density) {
    private val d: Float = density.density
    val glow: ImageBitmap = softGlowBitmap((56 * d).toInt())
    val streakVertical: ImageBitmap =
        streakBitmap(longPx = (44 * d).toInt(), shortPx = (7 * d).toInt(), vertical = true)
    val streakHorizontal: ImageBitmap =
        streakBitmap(longPx = (60 * d).toInt(), shortPx = (13 * d).toInt(), vertical = false)
    val veil: ImageBitmap = veilBitmap((92 * d).toInt(), (300 * d).toInt())
}

/**
 * Turn a scene's symbolic [ShapeKind] list into concrete [ParticleShape] instances.
 * Path / Text shapes are expanded across the scene's size list for visual variety,
 * because the engine sizes those shapes by their intrinsic geometry, not by `particleSizes`.
 */
fun resolveShapes(
    scene: SceneSpec,
    density: Density,
    measurer: TextMeasurer,
    assets: StudioAssets,
): List<ParticleShape> {
    val out = mutableListOf<ParticleShape>()
    scene.shapeKinds.forEach { kind ->
        when (kind) {
            ShapeKind.CIRCLE -> out += ParticleShape.Circle
            ShapeKind.GLOW_IMAGE -> out += ParticleShape.Image(assets.glow)
            ShapeKind.VEIL_IMAGE -> out += ParticleShape.Image(assets.veil)
            ShapeKind.STREAK_IMAGE -> {
                // A rotating scene (meteor) needs a horizontal streak it can orient along
                // its travel vector; a non-rotating one (rain) wants a ready-vertical drop.
                out += ParticleShape.Image(if (scene.rotates) assets.streakHorizontal else assets.streakVertical)
            }
            ShapeKind.STAR_PATH -> scene.sizes.forEach { s ->
                val rPx = with(density) { s.width.toPx() } / 2f
                out += ParticleShape.PathShape(starPath(rPx.coerceAtLeast(4f)))
            }
            ShapeKind.HEART_PATH -> scene.sizes.forEach { s ->
                val rPx = with(density) { s.width.toPx() } / 2f
                out += ParticleShape.PathShape(heartPath(rPx.coerceAtLeast(4f) * 0.8f))
            }
            ShapeKind.TEXT_EMOJI -> scene.emojis.forEachIndexed { i, e ->
                val s = scene.sizes[i % scene.sizes.size]
                out += ParticleShape.Text(
                    text = e,
                    textStyle = TextStyle(fontSize = s.width.value.sp),
                    textMeasurer = measurer,
                )
            }
        }
    }
    if (out.isEmpty()) out += ParticleShape.Circle
    return out
}

/**
 * Build the live [CanvasEmitterConfig] for a scene, given the values that the UI animates
 * or the user overrides (emitter position, birth-rate, gravity, edge behaviour).
 * Everything else comes straight from the immutable [SceneSpec].
 */
fun SceneSpec.toConfig(
    center: DpOffset,
    regionSize: DpSize,
    shapes: List<ParticleShape>,
    particlePerSecond: Int,
    gravityStrength: Float,
    edge: EdgeBehavior,
): CanvasEmitterConfig = CanvasEmitterConfig(
    particlePerSecond = particlePerSecond.coerceAtLeast(0),
    emitterCenter = center,
    startRegionShape = regionShape,
    startRegionSize = regionSize,
    particleShapes = shapes,
    lifespanRange = lifespan,
    fadeOutTime = fadeOut,
    scaleTime = scaleTime,
    colors = colors,
    particleSizes = sizes,
    spread = spread,
    blendMode = blend,
    alphaEasing = alphaEasing,
    scaleEasing = scaleEasing,
    initialForce = initialForce,
    rotationRange = rotation,
    startScaleRange = startScale,
    targetScaleRange = targetScale,
    gravityStrength = gravityStrength,
    gravityAngle = gravityAngle,
    edgeBehavior = edge,
    hideInStartRegion = hideInStartRegion,
)

/**
 * A faint, slow, large-glow "dust" field in the scene's palette, drawn *behind* the main
 * emitter to give the stage real foreground/background depth (parallax). Reuses the soft
 * glow sprite; colours are dimmed so it never competes with the live scene.
 */
fun SceneSpec.toBackdropConfig(
    center: DpOffset,
    regionSize: DpSize,
    glowShapes: List<ParticleShape>,
    particlePerSecond: Int,
): CanvasEmitterConfig = CanvasEmitterConfig(
    particlePerSecond = particlePerSecond.coerceAtLeast(0),
    emitterCenter = center,
    startRegionShape = CanvasEmitterConfig.Shape.OVAL,
    startRegionSize = regionSize,
    particleShapes = glowShapes,
    lifespanRange = 12000..20000,
    fadeOutTime = 12000..20000,
    scaleTime = 9000..15000,
    colors = colors.map { it.copy(alpha = it.alpha * 0.30f) },
    particleSizes = sizes,
    spread = -180..180,
    blendMode = BlendMode.Screen,
    initialForce = 6..18,
    startScaleRange = 0..0,
    targetScaleRange = 2..3,
    gravityStrength = 0f,
    edgeBehavior = EdgeBehavior.None,
    hideInStartRegion = false,
)
