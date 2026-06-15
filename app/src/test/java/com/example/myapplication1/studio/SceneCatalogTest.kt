package com.example.myapplication1.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the engine-contract invariants every hand-authored [SceneSpec] must satisfy.
 * These are pure data assertions (no Android framework), so they run on the JVM.
 */
class SceneCatalogTest {

    private val scenes = SceneCatalog.scenes

    @Test
    fun catalogIsNonEmptyWithUniqueIds() {
        assertTrue("catalog should not be empty", scenes.isNotEmpty())
        val ids = scenes.map { it.id }
        assertEquals("scene ids must be unique", ids.size, ids.toSet().size)
    }

    @Test
    fun everySceneHasWellFormedVisuals() {
        scenes.forEach { s ->
            assertEquals("${s.id}: gradient must have exactly 3 stops", 3, s.gradient.size)
            assertTrue("${s.id}: colors must not be empty", s.colors.isNotEmpty())
            assertTrue("${s.id}: sizes must not be empty", s.sizes.isNotEmpty())
            assertTrue("${s.id}: shapeKinds must not be empty", s.shapeKinds.isNotEmpty())
            assertEquals("${s.id}: accent should be fully opaque", 1f, s.accent.alpha, 0.001f)
        }
    }

    @Test
    fun everyRangeIsOrderedAndSane() {
        scenes.forEach { s ->
            assertOrdered(s.id, "lifespan", s.lifespan)
            assertOrdered(s.id, "fadeOut", s.fadeOut)
            assertOrdered(s.id, "scaleTime", s.scaleTime)
            assertOrdered(s.id, "spread", s.spread)
            assertOrdered(s.id, "initialForce", s.initialForce)
            assertOrdered(s.id, "rotation", s.rotation)
            assertOrdered(s.id, "startScale", s.startScale)
            assertOrdered(s.id, "targetScale", s.targetScale)
            assertTrue("${s.id}: lifespan must be positive", s.lifespan.first > 0)
            assertTrue("${s.id}: fadeOut must be non-negative", s.fadeOut.first >= 0)
            assertTrue("${s.id}: scale ranges must be non-negative", s.startScale.first >= 0 && s.targetScale.first >= 0)
            assertTrue("${s.id}: gravity must be non-negative", s.gravityStrength >= 0f)
        }
    }

    @Test
    fun birthRateIsWithinSliderBounds() {
        scenes.forEach { s ->
            assertTrue(
                "${s.id}: particlePerSecond ${s.particlePerSecond} should sit within [${MIN_PPS}, ${MAX_PPS}]",
                s.particlePerSecond in MIN_PPS.toInt()..MAX_PPS.toInt(),
            )
        }
    }

    @Test
    fun emojisPresentIffTextShapeUsed() {
        scenes.forEach { s ->
            val usesText = ShapeKind.TEXT_EMOJI in s.shapeKinds
            assertEquals(
                "${s.id}: emojis must be non-empty iff a TEXT_EMOJI shape is used",
                usesText,
                s.emojis.isNotEmpty(),
            )
        }
    }

    private fun assertOrdered(id: String, name: String, range: IntRange) {
        assertTrue("$id: $name range must have first <= last (${range.first}..${range.last})", range.first <= range.last)
    }
}
