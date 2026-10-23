package com.mvp.core.files

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dossier vide, propre au test, avec l'implémentation de la plateforme. */
expect fun createTestFileStore(): PrivateFileStore

class PrivateFileStoreTest {

    private val store = createTestFileStore()

    @AfterTest
    fun cleanUp() = store.deleteAll()

    @Test
    fun writtenFileCanBeReadBackAndListed() {
        store.write("a1", byteArrayOf(1, 2, 3))
        store.write("b2", ByteArray(0))

        assertContentEquals(byteArrayOf(1, 2, 3), store.read("a1"))
        assertContentEquals(ByteArray(0), store.read("b2"))
        assertEquals(listOf("a1", "b2"), store.names().sorted())
        assertTrue(store.path("a1").endsWith("/a1"))
    }

    @Test
    fun writeReplacesTheWholeFile() {
        store.write("a", byteArrayOf(1, 2, 3, 4))
        store.write("a", byteArrayOf(9))

        assertContentEquals(byteArrayOf(9), store.read("a"))
    }

    @Test
    fun deleteAndDeleteAllRemoveFiles() {
        store.write("a", byteArrayOf(1))
        store.write("b", byteArrayOf(2))

        store.delete("a")
        store.delete("inconnu")
        assertNull(store.read("a"))
        assertEquals(listOf("b"), store.names())

        store.deleteAll()
        assertEquals(emptyList(), store.names())
        // Le dossier est recréé à l'écriture suivante (nouvelle connexion).
        store.write("c", byteArrayOf(3))
        assertEquals(listOf("c"), store.names())
    }

    @Test
    fun namesThatCouldLeaveTheFolderAreRefused() {
        listOf("../a", "a/b", "", ".", "a.jpg", "x".repeat(65)).forEach { name ->
            assertFailsWith<IllegalArgumentException> { store.write(name, byteArrayOf(1)) }
            assertFailsWith<IllegalArgumentException> { store.read(name) }
            assertFailsWith<IllegalArgumentException> { store.delete(name) }
        }
        assertEquals(emptyList(), store.names())
    }
}
