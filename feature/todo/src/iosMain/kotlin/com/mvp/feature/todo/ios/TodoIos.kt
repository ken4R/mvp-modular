package com.mvp.feature.todo.ios

import com.mvp.feature.todo.domain.model.TodoFilter
import com.mvp.feature.todo.domain.model.TodoPriority
import com.mvp.feature.todo.domain.model.TodoPriorityChange
import com.mvp.feature.todo.domain.model.TodoStatusFilter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * Filtre des tâches construit depuis Swift, qui renomme les entrées des enum Kotlin : [status] est le nom Kotlin
 * de l'entrée de `TodoStatusFilter` (« All », « Pending », « Done ») ; un nom inconnu vaut « All ».
 * [byPriority] : tri par priorité au lieu de l'ordre manuel (ADR 0024).
 */
fun todoFilter(
    query: String,
    status: String,
    withReminder: Boolean,
    withAttachments: Boolean,
    byPriority: Boolean,
): TodoFilter = TodoFilter(
    query = query,
    status = TodoStatusFilter.entries.firstOrNull { it.name == status } ?: TodoStatusFilter.All,
    withReminder = withReminder,
    withAttachments = withAttachments,
    byPriority = byPriority,
)

/**
 * Changement de priorité construit depuis Swift : [priority] est le nom Kotlin de l'entrée de `TodoPriority`
 * (« None », « Low », « Medium », « High ») ; un nom inconnu vaut « None » (ADR 0024).
 */
fun todoPriorityChange(todoId: String, priority: String): TodoPriorityChange = TodoPriorityChange(
    todoId = todoId,
    priority = TodoPriority.entries.firstOrNull { it.name == priority } ?: TodoPriority.None,
)

/**
 * Contenu d'un fichier choisi en Swift (`Data`), pour `NewTodoAttachment` : une seule copie mémoire, au lieu
 * d'un octet à la fois depuis Swift.
 */
@OptIn(ExperimentalForeignApi::class)
fun bytesOf(data: NSData): ByteArray {
    val size = data.length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return bytes
}
