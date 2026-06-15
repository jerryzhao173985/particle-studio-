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

/** True when the device can run AGSL runtime shaders (Android 13 / API 33+). */
val agslSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

// A flowing aurora of accent-tinted light filaments + a soft glow that swells toward the
// steered source. Output alpha is the luminance so it composites additively (Plus) over the
// scene's gradient — adding light rather than replacing the backdrop.
private const val AURORA_AGSL = """
uniform float2 iResolution;
uniform float iTime;
uniform float3 iAccent;
uniform float iIntensity;
uniform float2 iTouch;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution;
    float t = iTime;

    // Two drifting horizontal light bands (the "curtain").
    float b1 = 1.0 - abs(uv.y - (0.42 + 0.12 * sin(uv.x * 3.0 + t))) * 6.0;
    float b2 = 1.0 - abs(uv.y - (0.60 + 0.10 * sin(uv.x * 5.0 - t * 1.3))) * 8.0;
    float band = max(b1, 0.0) + 0.6 * max(b2, 0.0);
    band *= (0.35 + 0.65 * iIntensity);

    // Soft glow that follows the emitter / finger.
    float2 d = (fragCoord - iTouch) / iResolution.y;
    float glow = exp(-dot(d, d) * 4.0) * (0.4 + 0.6 * iIntensity);

    float v = clamp(band * 0.45 + glow * 0.8, 0.0, 1.0);
    half3 col = half3(iAccent) * v;
    return half4(col, v);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun newAuroraShader(): RuntimeShader? = runCatching { RuntimeShader(AURORA_AGSL) }.getOrNull()

/**
 * A GPU AGSL "aurora" layer, drawn additively over the scene gradient. No-op below API 33 or
 * under reduced-motion (the scene's own gradient/spotlight remain the backdrop). [touchPx] is
 * the current emitter position in pixels so the glow tracks drag-to-steer.
 */
@Composable
fun AuroraShaderLayer(
    accent: Color,
    intensity: Float,
    touchPx: Offset,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!agslSupported || reduceMotion) return
    AuroraShaderLayerImpl(accent, intensity, touchPx, modifier)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AuroraShaderLayerImpl(accent: Color, intensity: Float, touchPx: Offset, modifier: Modifier) {
    val shader = remember { newAuroraShader() } ?: return
    val brush = remember(shader) { ShaderBrush(shader) }
    val clock = rememberInfiniteTransition(label = "sky")
    val time by clock.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
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
                    drawRect(brush, blendMode = BlendMode.Plus, alpha = 0.38f)
                }
            }
    )
}
