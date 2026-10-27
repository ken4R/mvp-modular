package com.mvp.feature.todo.domain.model

import com.mvp.core.common.domain.search.SearchText

/** Filtre sur l'état d'une tâche. */
enum class TodoStatusFilter { All, Pending, Done }

/**
 * Recherche et filtres de la liste des tâches. [query] : mots cherchés dans le titre et la note, sans casse ni
 * accents ; tous doivent apparaître. Les filtres se cumulent. [withReminder] : rappel à l'heure ou par lieu.
 * [byPriority] : trie par priorité (la plus haute d'abord, puis l'ordre manuel) au lieu de l'ordre manuel seul ; ce
 * n'est pas un filtre, il ne retire aucune tâche (ADR 0024).
 */
data class TodoFilter(
    val query: String = "",
    val status: TodoStatusFilter = TodoStatusFilter.All,
    val withReminder: Boolean = false,
    val withAttachments: Boolean = false,
    val byPriority: Boolean = false,
) {
    /** `false` : la liste complète est affichée (le tri n'y change rien). */
    val isActive: Boolean
        get() = SearchText.terms(query).isNotEmpty() || status != TodoStatusFilter.All || withReminder || withAttachments

    /** Glisser-déposer possible : la liste suit l'ordre manuel, pas la priorité. */
    val allowsReordering: Boolean
        get() = !byPriority

    /** Prédicat de ce filtre ; la recherche n'est repliée qu'une fois pour toute la liste. */
    fun matcher(): (Todo) -> Boolean {
        val terms = SearchText.terms(query)
        return { todo ->
            when (status) {
                TodoStatusFilter.All -> true
                TodoStatusFilter.Pending -> !todo.isDone
                TodoStatusFilter.Done -> todo.isDone
            } &&
                (!withReminder || todo.reminderAt != null || todo.locationReminder != null) &&
                (!withAttachments || todo.attachmentCount > 0) &&
                SearchText.matches(terms, todo.title, todo.note)
        }
    }
}

/**
 * Résultat d'une recherche : [todos] à afficher, et [all] les tâches sans filtre (indication des rappels
 * désactivés, message « aucune tâche » ou « aucun résultat »). Les deux dans l'ordre manuel, sauf [todos] triées par
 * priorité si [TodoFilter.byPriority].
 */
data class FilteredTodos(val todos: List<Todo>, val all: List<Todo>)
