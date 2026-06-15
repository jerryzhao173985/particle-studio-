package com.example.myapplication1.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.piotrprus.particleemitter.EdgeBehavior
import kotlin.math.roundToInt

/** Slider bounds for the live birth-rate control. */
internal const val MIN_PPS = 10f
internal const val MAX_PPS = 350f
/** Extra particles/second injected by a tap "burst". */
internal const val BURST_BOOST = 170f

internal data class EdgeOption(val label: String, val behavior: EdgeBehavior)

internal val EDGE_OPTIONS = listOf(
    EdgeOption("None", EdgeBehavior.None),
    EdgeOption("Bounce", EdgeBehavior.Bounce(damping = 0.6f)),
    EdgeOption("Stick", EdgeBehavior.Stick),
    EdgeOption("Wrap", EdgeBehavior.Wrap),
)

/** Index of an [EdgeBehavior] within [EDGE_OPTIONS] by type (damping is ignored for matching). */
internal fun edgeIndexOf(behavior: EdgeBehavior): Int =
    EDGE_OPTIONS.indexOfFirst { it.behavior::class == behavior::class }.coerceAtLeast(0)

@Composable
internal fun LiveControlsPanel(
    accent: Color,
    pps: Float,
    onPps: (Float) -> Unit,
    gravityOn: Boolean,
    onGravity: (Boolean) -> Unit,
    currentEdge: EdgeBehavior,
    onEdge: (EdgeBehavior) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(StudioTokens.ChromeCorner),
                ambientColor = Color.Black.copy(alpha = 0.6f),
                spotColor = accent.copy(alpha = 0.35f),
            )
            // Consume gestures over the panel so reaching for a control never flings the wand.
            .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume() } },
        color = MaterialTheme.colorScheme.surface.copy(alpha = StudioTokens.ChromeSurfaceAlpha),
        contentColor = Color.White,
        shape = RoundedCornerShape(StudioTokens.ChromeCorner),
        border = BorderStroke(1.dp, Color.White.copy(alpha = StudioTokens.ChromeBorderAlpha)),
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Intensity
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Intensity", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(72.dp))
                Slider(
                    value = pps.coerceIn(MIN_PPS, MAX_PPS),
                    onValueChange = onPps,
                    valueRange = MIN_PPS..MAX_PPS,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = "Particle intensity"
                            stateDescription = "${pps.roundToInt()} particles per second"
                        },
                )
                Text(
                    text = "${pps.roundToInt()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    modifier = Modifier.width(40.dp),
                )
            }

            // Gravity
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Gravity", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = gravityOn,
                    onCheckedChange = onGravity,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = accent.copy(alpha = 0.7f),
                        checkedThumbColor = Color.White,
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = if (gravityOn) "Gravity on" else "Gravity off"
                    },
                )
            }

            // Edge behaviour header + Reset
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Edges", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = onReset,
                    label = { Text("Reset") },
                    modifier = Modifier.semantics { contentDescription = "Reset scene to defaults" },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EDGE_OPTIONS.forEach { option ->
                    val selected = currentEdge::class == option.behavior::class
                    FilterChip(
                        selected = selected,
                        onClick = { onEdge(option.behavior) },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.22f),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.05f),
                            labelColor = Color.White.copy(alpha = 0.78f),
                        ),
                        modifier = Modifier.semantics {
                            this.selected = selected
                            role = Role.RadioButton
                            contentDescription = "${option.label} edges"
                        },
                    )
                }
            }
        }
    }
}
