package com.example.myapplication1.studio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The small set of genuinely-repeated, load-bearing chrome values. Deliberately tiny. */
internal object StudioTokens {
    val ChromeCorner = 24.dp
    val CardCorner = 18.dp
    const val ChromeSurfaceAlpha = 0.60f
    const val ChromeBorderAlpha = 0.12f
    val TopScrim = 140.dp
    val BottomScrim = 300.dp
    const val TopScrimAlpha = 0.45f
    const val BottomScrimAlpha = 0.62f
}

@Composable
internal fun StudioTopBar(
    scene: SceneSpec,
    accent: Color,
    index: Int,
    total: Int,
    infoOpen: Boolean,
    onToggleInfo: () -> Unit,
    autoTour: Boolean,
    onToggleTour: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            val eyebrowAlpha = if (reduceMotion) 1f else {
                val shimmer = rememberInfiniteShimmer()
                shimmer
            }
            Text(
                text = "SCENE $index OF $total",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
                color = accent.copy(alpha = eyebrowAlpha),
            )
            Spacer(Modifier.height(2.dp))
            AnimatedContent(
                targetState = scene.title,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        (fadeIn(tween(450)) + slideInVertically { it / 6 }) togetherWith fadeOut(tween(200))
                    }
                },
                label = "title",
            ) { title ->
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                        lineHeight = 32.sp,
                        color = Color.White,
                        shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), blurRadius = 10f),
                    ),
                )
            }
            Text(
                text = scene.subtitle,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlyphToggle(
                glyph = if (autoTour) "❚❚" else "▶",
                active = autoTour,
                accent = accent,
                description = if (autoTour) "Pause auto-tour" else "Play auto-tour",
                onClick = onToggleTour,
            )
            GlyphToggle(
                glyph = if (infoOpen) "✕" else "ⓘ",
                active = infoOpen,
                accent = accent,
                description = if (infoOpen) "Hide scene details" else "Show scene details",
                onClick = onToggleInfo,
            )
        }
    }
}

@Composable
private fun rememberInfiniteShimmer(): Float {
    val transition = rememberInfiniteTransition(label = "eyebrow")
    val alpha by transition.animateFloatLooping(0.7f, 1f, 1600)
    return alpha
}

@Composable
private fun GlyphToggle(
    glyph: String,
    active: Boolean,
    accent: Color,
    description: String,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        if (active) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f),
        label = "toggleBg",
    )
    Surface(
        color = bg,
        contentColor = Color.White,
        shape = CircleShape,
        border = BorderStroke(1.dp, accent.copy(alpha = if (active) 0.85f else 0.25f)),
        modifier = Modifier
            .size(46.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = glyph, fontSize = 15.sp, color = if (active) accent else Color.White)
        }
    }
}

@Composable
internal fun SceneInfoPanel(scene: SceneSpec, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = StudioTokens.ChromeSurfaceAlpha),
        contentColor = Color.White,
        shape = RoundedCornerShape(StudioTokens.ChromeCorner),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = scene.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "WHAT IT SHOWCASES",
                fontSize = 11.sp,
                letterSpacing = 1.4.sp,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            scene.features.forEach { feature ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("•  ", color = accent, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = feature,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Tip: drag anywhere to steer the source · tap to puff a burst",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
internal fun SceneSelectorRail(
    scenes: List<SceneSpec>,
    accent: Color,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.animateScrollToItem(selected) }
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            // Fade the rail at both edges instead of hard-clipping off-screen cards.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.04f to Color.Black,
                        0.96f to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(scenes, key = { _, s -> s.id }) { i, s ->
            SceneCard(scene = s, active = i == selected, position = i + 1, onClick = { onSelect(i) })
        }
    }
}

@Composable
private fun SceneCard(
    scene: SceneSpec,
    active: Boolean,
    position: Int,
    onClick: () -> Unit,
) {
    val cardModifier = if (active) {
        Modifier.shadow(
            elevation = 8.dp,
            shape = RoundedCornerShape(StudioTokens.CardCorner),
            ambientColor = scene.accent,
            spotColor = scene.accent,
        )
    } else {
        Modifier
    }
    Surface(
        color = if (active) scene.accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
        contentColor = Color.White,
        shape = RoundedCornerShape(StudioTokens.CardCorner),
        border = if (active) BorderStroke(1.5.dp, scene.accent) else null,
        modifier = cardModifier
            .width(132.dp)
            .clickable(onClick = onClick)
            .semantics {
                this.selected = active
                role = Role.Tab
                contentDescription = "${scene.title}, scene $position"
            },
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(scene.accent, scene.accent.copy(alpha = 0.35f))))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = scene.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) Color.White else Color.White.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A looping float for subtle ambient shimmer of chrome (ping-pongs between [from] and [to]). */
@Composable
internal fun InfiniteTransition.animateFloatLooping(from: Float, to: Float, periodMs: Int): State<Float> =
    animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(tween(periodMs), RepeatMode.Reverse),
        label = "loop",
    )
