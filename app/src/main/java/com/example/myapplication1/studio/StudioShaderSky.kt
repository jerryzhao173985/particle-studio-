package com.example.myapplication1.studio

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.PI

/** True when the device can run AGSL runtime shaders (Android 13 / API 33+). */
val agslSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/*
 * Per-scene GPU "atmosphere" shaders. Each is the living medium a scene's particles move
 * through — composited additively (Plus) over the scene gradient so it ADDS light, never
 * replaces the backdrop. All three declare and read the SAME uniform set (iResolution, iTime,
 * iAccent, iIntensity, iTouch) so a uniform is never stripped (setting a stripped uniform
 * throws), and so one draw path serves all of them. Output alpha = luminance for clean Plus.
 *
 * iTime is fed a value that loops over N·2π, and every time-coefficient below is chosen so
 * c·N is an integer — i.e. every term advances a whole number of 2π per loop — so the loop is
 * seamless with no visible jump.
 */

private const val AURORA_AGSL = """
uniform float2 iResolution;
uniform float iTime;
uniform float3 iAccent;
uniform float iIntensity;
uniform float2 iTouch;
half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution;
    float t = iTime;
    float b1 = 1.0 - abs(uv.y - (0.42 + 0.12 * sin(uv.x * 3.0 + t))) * 6.0;
    float b2 = 1.0 - abs(uv.y - (0.60 + 0.10 * sin(uv.x * 5.0 - t * 1.3))) * 8.0;
    float band = (max(b1, 0.0) + 0.6 * max(b2, 0.0)) * (0.35 + 0.65 * iIntensity);
    float2 d = (fragCoord - iTouch) / iResolution.y;
    float glow = exp(-dot(d, d) * 4.0) * (0.4 + 0.6 * iIntensity);
    float v = clamp(band * 0.45 + glow * 0.8, 0.0, 1.0);
    return half4(half3(iAccent) * v, v);
}
"""

private const val NEBULA_AGSL = """
uniform float2 iResolution;
uniform float iTime;
uniform float3 iAccent;
uniform float iIntensity;
uniform float2 iTouch;
half4 main(float2 fragCoord) {
    float2 uv = (fragCoord - 0.5 * iResolution) / iResolution.y;
    float t = iTime;
    float r = length(uv);
    float a = atan(uv.y, uv.x) + t * 0.3 + r * 2.0;          // swirl
    float2 p = float2(cos(a), sin(a)) * r;
    float n = 0.5 + 0.5 * sin(p.x * 6.0 + t) * sin(p.y * 5.0 - t * 0.7);
    float cloud = smoothstep(0.25, 1.0, n) * (0.3 + 0.7 * iIntensity);
    float core = exp(-r * r * 2.5) * (0.4 + 0.6 * iIntensity);
    float2 d = (fragCoord - iTouch) / iResolution.y;
    float glow = exp(-dot(d, d) * 3.5) * (0.3 + 0.5 * iIntensity);
    float v = clamp(cloud * 0.5 + core * 0.5 + glow * 0.6, 0.0, 1.0);
    return half4(half3(iAccent) * v, v);
}
"""

// A soft, base-confined warm glow that gently breathes upward — natural campfire heat, NOT a
// busy shimmer. Low spatial frequency (a few wide columns of warm air, not stripes), smoothly
// faded so it lives only in the lower third and never clutters the top, and low contrast so it
// reads "in accordance" with the rising embers rather than fighting them.
private const val HEAT_AGSL = """
uniform float2 iResolution;
uniform float iTime;
uniform float3 iAccent;
uniform float iIntensity;
uniform float2 iTouch;
half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution;
    float t = iTime;
    // Confined to the base: full at the bottom, smoothly gone by ~35% up.
    float base = smoothstep(0.62, 1.0, uv.y);
    // Gentle, low-frequency rising undulation (a couple of soft warm columns).
    float undulate = 0.7 + 0.3 * sin(uv.x * 3.0 + sin(uv.y * 2.5 - t * 1.1) * 0.7 + t * 0.5);
    float heat = base * base * undulate * (0.4 + 0.6 * iIntensity);
    // A faint warmth pooling at the steered source — like a breath on the embers.
    float2 d = (fragCoord - iTouch) / iResolution.y;
    float pool = exp(-dot(d, d) * 4.0) * 0.2 * iIntensity;
    float v = clamp(heat + pool, 0.0, 1.0);
    return half4(half3(iAccent) * v, v * 0.7);
}
"""

private fun srcFor(atmosphere: Atmosphere): String? = when (atmosphere) {
    Atmosphere.None -> null
    Atmosphere.Aurora -> AURORA_AGSL
    Atmosphere.Nebula -> NEBULA_AGSL
    Atmosphere.Heat -> HEAT_AGSL
}

private fun alphaFor(atmosphere: Atmosphere): Float = when (atmosphere) {
    Atmosphere.Heat -> 0.34f   // confined to the base + low-contrast, so it stays gentle
    else -> 0.36f
}

/**
 * The GPU atmosphere layer for [atmosphere], drawn additively over the scene gradient. No-op for
 * [Atmosphere.None], below API 33, or under reduced-motion (the gradient remains the backdrop).
 * [touchPx] is the emitter position in pixels so the field's glow tracks the steered source.
 */
@Composable
fun AtmosphereLayer(
    atmosphere: Atmosphere,
    accent: Color,
    intensity: Float,
    touchPx: Offset,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    if (atmosphere == Atmosphere.None || !agslSupported || reduceMotion) return
    AtmosphereLayerImpl(atmosphere, accent, intensity, touchPx, modifier)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AtmosphereLayerImpl(
    atmosphere: Atmosphere,
    accent: Color,
    intensity: Float,
    touchPx: Offset,
    modifier: Modifier,
) {
    val shader = remember(atmosphere) {
        srcFor(atmosphere)?.let { src -> runCatching { RuntimeShader(src) }.getOrNull() }
    } ?: return
    val brush = remember(shader) { ShaderBrush(shader) }
    val alpha = alphaFor(atmosphere)
    val clock = rememberInfiniteTransition(label = "sky")
    // Loop over 600·2π so every integer-scaled time term completes whole cycles — seamless.
    val time by clock.animateFloat(
        initialValue = 0f,
        targetValue = (600.0 * 2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(600_000, easing = LinearEasing), RepeatMode.Restart),
        label = "skyTime",
    )
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                shader.setFloatUniform("iResolution", size.width, size.height)
                onDrawBehind {
                    shader.setFloatUniform("iTime", time)
                    shader.setFloatUniform("iAccent", accent.red, accent.green, accent.blue)
                    shader.setFloatUniform("iIntensity", intensity.coerceIn(0f, 1f))
                    shader.setFloatUniform("iTouch", touchPx.x, touchPx.y)
                    drawRect(brush, blendMode = BlendMode.Plus, alpha = alpha)
                }
            }
    )
}
