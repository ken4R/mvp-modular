package com.mvp.feature.todo

import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.order.OrderKey
import com.mvp.feature.todo.domain.order.TodoOrder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrderKeyTest {

    @Test
    fun keysAtTheEndStayShortAndIncrease() {
        val keys = generateSequence(OrderKey.between(null, null)) { OrderKey.between(it, null) }.take(5_000).toList()

        assertEquals(listOf("a0", "a1", "a2"), keys.take(3))
        assertEquals(keys.sorted(), keys)
        assertTrue(keys.all { OrderKey.isValid(it) && it.length <= 4 })
    }

    @Test
    fun keysAtTheStartDecrease() {
        val keys = generateSequence(OrderKey.between(null, null)) { OrderKey.between(null, it) }.take(5_000).toList()

        assertEquals(keys.sortedDescending(), keys)
        assertTrue(keys.all(OrderKey::isValid))
    }

    @Test
    fun thereIsAlwaysAKeyBetweenTwoOthers() {
        val random = Random(42)
        var keys = listOf(OrderKey.between(null, null))
        repeat(2_000) {
            val at = random.nextInt(keys.size + 1)
            val key = OrderKey.between(keys.getOrNull(at - 1), keys.getOrNull(at))
            assertTrue(OrderKey.isValid(key), key)
            keys = keys.take(at) + key + keys.drop(at)
        }

        assertEquals(keys.sorted(), keys)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun severalKeysBetweenTwoAreOrderedAndInside() {
        val keys = OrderKey.between("a0", "a1", 10)

        assertEquals(10, keys.size)
        assertEquals(keys.sorted(), keys)
        assertTrue(keys.all { it > "a0" && it < "a1" && OrderKey.isValid(it) })
        assertEquals(listOf("a0", "a1", "a2"), OrderKey.between(null, null, 3))
    }

    @Test
    fun keysFromTheMigrationAreValidAndExtendable() {
        // Migration 7.sqm : « h » + rowid sur 8 chiffres.
        val migrated = listOf("h00000001", "h00000002", "h00000010")

        assertTrue(migrated.all(OrderKey::isValid))
        assertEquals("h00000011", OrderKey.between("h00000010", null))
        val middle = OrderKey.between("h00000001", "h00000002")
        assertTrue(middle > "h00000001" && middle < "h00000002")
    }

    @Test
    fun invalidKeysAreRejected() {
        listOf("", "a", "a00", "b0", "a0/", "a0 ", "é0", "A" + "0".repeat(26), "a1" + "x".repeat(OrderKey.MAX_LENGTH))
            .forEach { assertFalse(OrderKey.isValid(it), it) }
        assertFailsWith<IllegalArgumentException> { OrderKey.between("a1", "a0") }
        assertFailsWith<IllegalArgumentException> { OrderKey.between("a0", "a0") }
        assertFailsWith<IllegalArgumentException> { OrderKey.between("pas une clé", null) }
    }

    @Test
    fun moveChangesOnlyTheMovedTodo() {
        val section = todos("a" to "a0", "b" to "a1", "c" to "a2")

        val positions = TodoOrder.planMove(section, "c", 0)

        assertEquals(setOf("c"), positions.keys)
        assertEquals(listOf("c", "a", "b"), reorder(section, positions))
    }

    @Test
    fun dropInPlaceChangesNothing() {
        val section = todos("a" to "a0", "b" to "a1", "c" to "a2")

        assertEquals(emptyMap(), TodoOrder.planMove(section, "b", 1))
        assertEquals(emptyMap(), TodoOrder.planMove(section, "inconnue", 0))
    }

    @Test
    fun followingTodosWithTheSameKeyAreRekeyedToo() {
        // Deux appareils ont créé la même clé : « b » et « c » sont à égalité.
        val section = todos("a" to "a0", "b" to "a1", "c" to "a1", "d" to "a2")

        val positions = TodoOrder.planMove(section, "a", 1)

        assertEquals(setOf("a", "c"), positions.keys)
        assertEquals(listOf("b", "a", "c", "d"), reorder(section, positions))
    }

    @Test
    fun missingOrInvalidKeysRenumberTheWholeGroup() {
        // Tâches reçues sans clé, ou d'avant l'ordre manuel.
        val section = todos("a" to "", "b" to "", "c" to "a0")

        val positions = TodoOrder.planMove(section, "c", 1)

        assertEquals(setOf("a", "b", "c"), positions.keys)
        assertEquals(listOf("a", "c", "b"), reorder(section, positions))
    }

    @Test
    fun keysThatGrowTooLongRenumberTheGroup() {
        // Toujours lâchée au même endroit : la clé s'allonge, puis le groupe repart de clés courtes.
        var section = todos("a" to "a0", "b" to "a1")
        repeat(1_000) { i ->
            section = section + Todo("n$i", "Tâche", isDone = false, position = section.last().position.let { OrderKey.between(it, null) })
            val positions = TodoOrder.planMove(section, "n$i", 1)
            section = section.map { todo -> positions[todo.id]?.let { todo.copy(position = it) } ?: todo }
                .sortedBy { it.position }
            assertEquals("n$i", section[1].id)
        }

        assertTrue(section.all { it.position.length <= OrderKey.MAX_LENGTH })
        assertEquals(section.map { it.position }.toSet().size, section.size)
    }

    @Test
    fun nextPositionGoesAfterTheLastKey() {
        assertEquals("a0", TodoOrder.nextPosition(null))
        assertEquals("a0", TodoOrder.nextPosition(""))
        assertEquals("h00000003", TodoOrder.nextPosition("h00000002"))
    }

    private fun todos(vararg idAndPosition: Pair<String, String>) =
        idAndPosition.map { (id, position) -> Todo(id, id, isDone = false, position = position) }

    /** Ordre après le déplacement, comme la base : par clé, puis dans l'ordre d'avant à clé égale. */
    private fun reorder(section: List<Todo>, positions: Map<String, String>): List<String> =
        section.map { it.copy(position = positions[it.id] ?: it.position) }.sortedBy { it.position }.map { it.id }
}
