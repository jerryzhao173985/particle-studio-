package com.example.myapplication1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.myapplication1.studio.ParticleStudioScreen
import com.example.myapplication1.ui.theme.MyApplication1Theme

/**
 * Particle Studio — an immersive, full-screen showcase of the
 * `io.github.piotrprus:particle-emitter` Compose engine.
 *
 * The whole app is a single live stage: one
 * [dev.piotrprus.particleemitter.CanvasParticleEmitter] fills the screen and its
 * config is swapped between 14 curated scenes that, together, exercise every
 * feature of the package (all 5 start-region shapes, all 4 particle shapes, all 4
 * edge behaviours, gravity in every direction, blend modes, easing, hideInStartRegion).
 *
 * See [ParticleStudioScreen] for the UI shell and live controls.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // The showcase is built for a black stage, so we force the dark scheme
            // (and disable dynamic color) — chrome must stay legible over luminous particles.
            MyApplication1Theme(darkTheme = true, dynamicColor = false) {
                ParticleStudioScreen()
            }
        }
    }
}

@Preview
@Composable
private fun ParticleStudioPreview() {
    MyApplication1Theme(darkTheme = true, dynamicColor = false) {
        ParticleStudioScreen()
    }
}
