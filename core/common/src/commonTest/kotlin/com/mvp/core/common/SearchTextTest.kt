package com.mvp.core.common

import com.mvp.core.common.domain.search.SearchText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchTextTest {

    @Test
    fun foldRemovesCaseAccentsAndExtraSpaces() {
        assertEquals("creme brulee a l'oeuf", SearchText.fold("  Crème  BRÛLÉE\tà l'Œuf "))
    }

    @Test
    fun blankQueryMatchesEverything() {
        assertEquals(emptyList(), SearchText.terms("   "))
        assertTrue(SearchText.matches(SearchText.terms(""), "Acheter du pain"))
    }

    @Test
    fun everyWordMustAppearInOneOfTheFields() {
        val terms = SearchText.terms("pain tache")

        assertTrue(SearchText.matches(terms, "Acheter du pain", "Penser à la Tâche"))
        assertFalse(SearchText.matches(terms, "Acheter du pain", null))
    }

    @Test
    fun queryIsTruncated() {
        val terms = SearchText.terms("a".repeat(SearchText.MAX_QUERY_LENGTH + 50))

        assertEquals(listOf("a".repeat(SearchText.MAX_QUERY_LENGTH)), terms)
    }
}
