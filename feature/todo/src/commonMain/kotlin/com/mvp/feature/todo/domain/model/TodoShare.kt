package com.mvp.feature.todo.domain.model

/**
 * Demande de partage de la tâche [todoId] (ADR 0017). [includeNote] : la note enregistrée est ajoutée au texte,
 * seulement si l'utilisateur l'a choisi. Les pièces jointes ne sont jamais partagées.
 */
data class TodoShareRequest(val todoId: String, val includeNote: Boolean = false)

/**
 * Contenu confié à la feuille de partage du système. [subject] : objet (mail), [text] : message complet, qui se
 * termine par [link], le lien profond `mvpmodular://tasks/<id>` vers la tâche.
 */
data class TodoShare(val subject: String, val text: String, val link: String)
