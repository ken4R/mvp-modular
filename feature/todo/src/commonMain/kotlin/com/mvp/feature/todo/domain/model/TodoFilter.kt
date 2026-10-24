package com.mvp.feature.todo.domain.model

import com.mvp.core.common.domain.search.SearchText

/** Filtre sur l'état d'une tâche. */
enum class TodoStatusFilter { All, Pending, Done }

/**
 * Recherche et filtres de la liste des tâches. [query] : mots cherchés dans le titre et la note, sans casse ni
 * accents ; tous doivent apparaître. Les filtres se cumulent.
 */
data class TodoFilter(
    val query: String = "",
    val status: TodoStatusFilter = TodoStatusFilter.All,
    val withReminder: Boolean = false,
    val withAttachments: Boolean = false,
) {
    /** `false` : la liste complète est affichée. */
    val isActive: Boolean
        get() = SearchText.terms(query).isNotEmpty() || status != TodoStatusFilter.All || withReminder || withAttachments

    /** Prédicat de ce filtre ; la recherche n'est repliée qu'une fois pour toute la liste. */
    fun matcher(): (Todo) -> Boolean {
        val terms = SearchText.terms(query)
        return { todo ->
            when (status) {
                TodoStatusFilter.All -> true
                TodoStatusFilter.Pending -> !todo.isDone
                TodoStatusFilter.Done -> todo.isDone
            } &&
                (!withReminder || todo.reminderAt != null) &&
                (!withAttachments || todo.attachmentCount > 0) &&
                SearchText.matches(terms, todo.title, todo.note)
        }
    }
}

/**
 * Résultat d'une recherche : [todos] à afficher, et [all] les tâches sans filtre (indication des rappels
 * désactivés, message « aucune tâche » ou « aucun résultat »). Les deux dans le même ordre.
 */
data class FilteredTodos(val todos: List<Todo>, val all: List<Todo>)
