package com.mvp.core.deeplink

import com.mvp.core.deeplink.domain.DeepLinks
import com.mvp.core.deeplink.domain.model.DeepLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeepLinksTest {

    @Test
    fun eachScreenHasItsLink() {
        assertEquals(DeepLink.Tasks(), DeepLinks.parse("mvpmodular://tasks"))
        assertEquals(DeepLink.Tasks("3f2b1c9e-0d4a-4b7e-9c1f-2a6d8e5b7c40"), DeepLinks.parse("mvpmodular://tasks/3f2b1c9e-0d4a-4b7e-9c1f-2a6d8e5b7c40"))
        assertEquals(DeepLink.Weather, DeepLinks.parse("mvpmodular://weather"))
        assertEquals(DeepLink.Catalog(), DeepLinks.parse("mvpmodular://catalog"))
        assertEquals(DeepLink.Catalog(12), DeepLinks.parse("mvpmodular://catalog/products/12"))
        assertEquals(DeepLink.Settings, DeepLinks.parse("mvpmodular://settings"))
    }

    @Test
    fun schemeAndScreenIgnoreCaseAndTrailingSlash() {
        assertEquals(DeepLink.Weather, DeepLinks.parse("MVPModular://Weather/"))
        assertEquals(DeepLink.Catalog(7), DeepLinks.parse("mvpmodular://catalog/Products/7/"))
    }

    @Test
    fun queryAndFragmentAreIgnored() {
        assertEquals(DeepLink.Settings, DeepLinks.parse("mvpmodular://settings?notifications=off#top"))
        assertEquals(DeepLink.Tasks("abc"), DeepLinks.parse("mvpmodular://tasks/abc?delete=true"))
    }

    @Test
    fun otherSchemesAndUnknownScreensAreRejected() {
        listOf(
            "",
            "mvpmodular://",
            "mvpmodular:tasks",
            "https://example.com/tasks",
            "otherapp://tasks",
            "mvpmodular://unknown",
            "mvpmodular://weather/paris",
            "mvpmodular://settings/profile",
            "mvpmodular://tasks@evil.example/tasks",
        ).forEach { assertNull(DeepLinks.parse(it), it) }
    }

    @Test
    fun malformedIdsAreRejected() {
        listOf(
            "mvpmodular://tasks/a/b",
            "mvpmodular://tasks//abc",
            "mvpmodular://tasks/..",
            "mvpmodular://tasks/%2e%2e",
            "mvpmodular://tasks/" + "a".repeat(65),
            "mvpmodular://catalog/12",
            "mvpmodular://catalog/products",
            "mvpmodular://catalog/products/0",
            "mvpmodular://catalog/products/-3",
            "mvpmodular://catalog/products/abc",
            "mvpmodular://catalog/products/1234567890123456789",
            "mvpmodular://catalog/products/12/reviews",
        ).forEach { assertNull(DeepLinks.parse(it), it) }
    }

    @Test
    fun tooLongLinksAreRejected() {
        assertNull(DeepLinks.parse("mvpmodular://tasks?" + "x".repeat(600)))
    }

    @Test
    fun builtLinksAreReadBack() {
        listOf(
            DeepLink.Tasks(),
            DeepLink.Tasks("abc-123"),
            DeepLink.Weather,
            DeepLink.Catalog(),
            DeepLink.Catalog(42),
            DeepLink.Settings,
        ).forEach { assertEquals(it, DeepLinks.parse(DeepLinks.url(it))) }
        assertEquals("mvpmodular://tasks/abc-123", DeepLinks.url(DeepLink.Tasks("abc-123")))
        // Le titre dicté ne sort jamais dans un lien (ADR 0018).
        assertEquals("mvpmodular://tasks", DeepLinks.url(DeepLink.NewTask("Code de la porte 4521")))
    }

    @Test
    fun onlyAppTodoIdsAreValidInALink() {
        assertTrue(DeepLinks.isValidTodoId("0f8b2c1e-3a4d-4b5e-8f60-1a2b3c4d5e6f"))
        assertFalse(DeepLinks.isValidTodoId(""))
        assertFalse(DeepLinks.isValidTodoId("x https://piege.example"))
        assertFalse(DeepLinks.isValidTodoId("a/b"))
        assertFalse(DeepLinks.isValidTodoId("a".repeat(65)))
    }
}
