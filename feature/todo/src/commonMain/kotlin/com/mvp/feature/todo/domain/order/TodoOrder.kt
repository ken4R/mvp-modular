package com.mvp.feature.todo.domain.order

import com.mvp.feature.todo.domain.model.Todo

/** Ordre manuel des tâches (ADR 0024). */
object TodoOrder {

    /**
     * Nouvelles clés pour poser [movedId] à l'index [index] de [section] (tâches d'un même groupe, dans l'ordre
     * affiché, la tâche déplacée comprise). En général seule la tâche déplacée change de clé ; les suivantes aussi
     * si elles avaient la même clé que sa voisine du dessus (deux appareils ont pu créer la même). Les tâches sans
     * clé (reçues d'un serveur qui ne connaît pas l'ordre, triées en tête) en reçoivent une, et seulement elles : chaque
     * tâche réécrite part au serveur en entier et pourrait écraser une modification faite ailleurs. Une clé invalide
     * ailleurs qu'en tête, ou une clé devenue trop longue, fait renuméroter tout le groupe. Vide : rien à changer.
     */
    fun planMove(section: List<Todo>, movedId: String, index: Int): Map<String, String> {
        if (section.none { it.id == movedId }) return emptyMap()
        val missing = section.takeWhile { !OrderKey.isValid(it.position) }
        if (missing.isEmpty()) return planMoveWithKeys(section, movedId, index)
        val target = section.filterNot { it.id == movedId }.toMutableList()
        target.add(index.coerceIn(0, target.size), section.first { it.id == movedId })
        val firstKey = section.getOrNull(missing.size)?.position
        val keys = OrderKey.between(null, firstKey, missing.size)
        val rest = section.drop(missing.size)
        if (!keys.all(OrderKey::isValid) || rest.any { !OrderKey.isValid(it.position) }) return renumber(target)
        val given = missing.map { it.id }.zip(keys).toMap()
        val keyed = section.map { todo -> given[todo.id]?.let { todo.copy(position = it) } ?: todo }
        return given + planMoveWithKeys(keyed, movedId, index)
    }

    /** [planMove] quand toutes les tâches de [section] ont une clé. */
    private fun planMoveWithKeys(section: List<Todo>, movedId: String, index: Int): Map<String, String> {
        val moved = section.first { it.id == movedId }
        val target = section.filterNot { it.id == movedId }.toMutableList()
        target.add(index.coerceIn(0, target.size), moved)
        if (target.any { !OrderKey.isValid(it.position) }) return renumber(target)

        val at = target.indexOf(moved)
        val before = target.getOrNull(at - 1)?.position
        // Les tâches suivantes qui ne sont pas strictement après la voisine du dessus prennent aussi une clé.
        var end = at + 1
        while (end < target.size && before != null && target[end].position <= before) end++
        val after = target.getOrNull(end)?.position
        val alreadyInPlace = end == at + 1 &&
            (before == null || moved.position > before) && (after == null || moved.position < after)
        if (alreadyInPlace) return emptyMap()

        val keys = OrderKey.between(before, after, end - at)
        // Une clé que l'app refuserait à la réception (trop longue, ou la plus petite) ferait ignorer la tâche ailleurs.
        if (!keys.all(OrderKey::isValid)) return renumber(target)
        return target.subList(at, end).map { it.id }.zip(keys).toMap()
    }

    /** Clé d'une tâche ajoutée après [last] (la plus grande clé existante, `null` sans tâche). */
    // En général la partie entière est incrémentée et la clé reste courte. Après la plus grande partie entière
    // (« z » suivi de 26 « z », reçue d'un serveur), la clé s'allonge : `null` dès qu'elle ne serait plus valide, il
    // faut alors renuméroter ([renumber]).
    fun nextPosition(last: String?): String? =
        OrderKey.between(last?.takeIf(OrderKey::isValid), null).takeIf(OrderKey::isValid)

    /** Clés neuves et courtes pour [target], dans son ordre. */
    fun renumber(target: List<Todo>): Map<String, String> =
        target.map { it.id }.zip(OrderKey.between(null, null, target.size)).toMap()
}
