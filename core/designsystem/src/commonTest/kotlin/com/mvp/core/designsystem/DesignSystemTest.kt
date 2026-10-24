package com.mvp.core.designsystem

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            listOf(tokens.primary, tokens.background, tokens.surface, tokens.onSurface, tokens.highlight).forEach {
                assertTrue(it ushr 24 == 0xFFL, "couleur non opaque : ${it.toString(16)}")
            }
        }
    }

    @Test
    fun blueAccentIsTheDefaultPalette() {
        assertEquals(LightColors, DesignSystem.colors(darkTheme = false, accent = AccentColor.Blue))
        assertEquals(DarkColors, DesignSystem.colors(darkTheme = true, accent = AccentColor.Blue))
    }

    @Test
    fun accentChangesOnlyPrimaryAndHighlight() {
        AccentColor.entries.forEach { accent ->
            listOf(false, true).forEach { dark ->
                val base = DesignSystem.colors(dark)
                val tokens = DesignSystem.colors(dark, accent)
                assertEquals(base, tokens.copy(primary = base.primary, onPrimary = base.onPrimary, highlight = base.highlight))
            }
        }
        val primaries = AccentColor.entries.map { DesignSystem.colors(false, it).primary }
        assertEquals(primaries.size, primaries.toSet().size, "deux accents ont la même couleur")
    }

    @Test
    fun accentColorsAreReadable() {
        AccentColor.entries.forEach { accent ->
            listOf(false, true).forEach { dark ->
                val c = DesignSystem.colors(dark, accent)
                val label = "$accent ${if (dark) "sombre" else "clair"}"
                assertTrue(contrast(c.primary, c.onPrimary) >= 4.5, "$label : texte sur la couleur principale")
                assertTrue(contrast(c.primary, c.background) >= 4.5, "$label : couleur principale sur le fond")
                assertTrue(contrast(c.primary, c.surface) >= 4.5, "$label : couleur principale sur une surface")
                assertTrue(contrast(c.highlight, c.onSurface) >= 4.5, "$label : texte sur la mise en évidence")
                listOf(c.primary, c.onPrimary, c.highlight).forEach {
                    assertTrue(it ushr 24 == 0xFFL, "$label : couleur non opaque ${it.toString(16)}")
                }
            }
        }
    }

    @Test
    fun appearanceFollowsThemeMode() {
        assertTrue(Appearance(ThemeMode.System).isDark(systemDark = true))
        assertFalse(Appearance(ThemeMode.System).isDark(systemDark = false))
        assertFalse(Appearance(ThemeMode.Light).isDark(systemDark = true))
        assertTrue(Appearance(ThemeMode.Dark).isDark(systemDark = false))
        assertEquals(
            DesignSystem.colors(darkTheme = true, accent = AccentColor.Pink),
            DesignSystem.colors(Appearance(ThemeMode.Dark, AccentColor.Pink), systemDark = false),
        )
        assertEquals(Appearance(ThemeMode.System, AccentColor.Blue), Appearance.Default)
    }

    /** Rapport de contraste WCAG 2.1 entre deux couleurs ARGB opaques. */
    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
