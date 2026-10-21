package com.mvp.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesignSystemTest {

    @Test
    fun colorsFollowTheme() {
        assertEquals(LightColors, DesignSystem.colors(darkTheme = false))
        assertEquals(DarkColors, DesignSystem.colors(darkTheme = true))
        assertNotEquals(LightColors.background, DarkColors.background)
    }

    @Test
    fun spacingScaleIsIncreasing() {
        val s = DesignSystem.spacing
        val scale = listOf(s.xs, s.sm, s.md, s.lg, s.xl)
        assertEquals(scale.sorted(), scale)
    }

    @Test
    fun sizesArePositive() {
        val sizes = DesignSystem.sizes
        assertTrue(sizes.thumbnail > 0f && sizes.heroImage > sizes.thumbnail)
    }

    @Test
    fun colorsAreOpaque() {
        listOf(LightColors, DarkColors).forEach { tokens ->
            listOf(tokens.primary, tokens.background, tokens.surface, tokens.onSurface).forEach {
                assertTrue(it ushr 24 == 0xFFL, "couleur non opaque : ${it.toString(16)}")
            }
        }
    }
}
