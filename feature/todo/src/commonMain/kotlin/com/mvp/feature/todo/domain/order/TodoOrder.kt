package com.mvp.feature.todo.domain.order

import com.mvp.feature.todo.domain.model.Todo

/** Ordre manuel des tâches (ADR 0024). */
object TodoOrder {

    /**
     * Nouvelles clés pour poser [movedId] à l'index [index] de [section] (tâches d'un même groupe, dans l'ordre
     * affiché, la tâche déplacée comprise). En général seule la tâche déplacée change de clé ; les suivantes aussi
     * si elles avaient la même clé que sa voisine du dessus (deux appareils ont pu créer la même). Une clé absente
     * ou invalide, ou une clé devenue trop longue, fait renuméroter tout le groupe. Vide : rien à changer.
     */
    fun planMove(section: List<Todo>, movedId: String, index: Int): Map<String, String> {
        val moved = section.firstOrNull { it.id == movedId } ?: return emptyMap()
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
        if (keys.any { it.length > OrderKey.MAX_LENGTH }) return renumber(target)
        return target.subList(at, end).map { it.id }.zip(keys).toMap()
    }

    /** Clé d'une tâche ajoutée après [last] (la plus grande clé existante, `null` sans tâche). */
    // Ajouter en fin incrémente la partie entière : la clé reste courte (27 caractères au plus).
    fun nextPosition(last: String?): String = OrderKey.between(last?.takeIf(OrderKey::isValid), null)

    /** Clés neuves et courtes pour tout le groupe, dans l'ordre de [target]. */
    private fun renumber(target: List<Todo>): Map<String, String> =
        target.map { it.id }.zip(OrderKey.between(null, null, target.size)).toMap()
}
