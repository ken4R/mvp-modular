package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.applock.AppLockPreference
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.SessionExpiry
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.deeplink.domain.DeepLinks
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoWidgetItem
import com.mvp.feature.todo.domain.model.TodoWidgetState
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/**
 * Contenu du widget Tâches, recalculé à chaque changement des tâches, de la session ou du réglage Sécurité.
 * Session absente ou expirée : [TodoWidgetState.SignedOut] ; verrou activé : [TodoWidgetState.Locked].
 * Sinon les tâches non terminées (hors corbeille), celles qui ont un rappel d'abord (le plus proche en premier),
 * puis par priorité décroissante, puis dans l'ordre manuel de la liste (ADR 0024). En cas d'erreur, l'app ne doit rien montrer (voir les widgets).
 */
class ObserveTodoWidgetUseCase(
    private val repository: TodoRepository,
    private val session: SessionExpiry,
    private val appLock: AppLockPreference,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, TodoWidgetState>(dispatcher) {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun execute(params: NoParams): Flow<AppResult<TodoWidgetState>> =
        combine(session.observeExpiresAt(), appLock.observeAppLockEnabled(), repository.observeTodos()) { expiresAt, locked, todos ->
            when {
                expiresAt == null || expiresAt <= now() -> TodoWidgetState.SignedOut
                locked -> TodoWidgetState.Locked
                else -> tasks(todos, expiresAt)
            }
        }
            .distinctUntilChanged()
            // Rien ne réémet quand la session expire : on vide le widget nous-mêmes à son échéance.
            .transformLatest { state ->
                emit(state)
                if (state is TodoWidgetState.Tasks) {
                    delay(state.visibleUntil - now())
                    emit(TodoWidgetState.SignedOut)
                }
            }
            .distinctUntilChanged()
            .map { AppResult.Success(it) }

    private fun tasks(todos: List<Todo>, expiresAt: Long): TodoWidgetState.Tasks {
        val remaining = todos.filterNot { it.isDone }
        // sortedWith est stable : à rappel et priorité égaux, les tâches gardent l'ordre de la liste.
        val items = remaining
            .sortedWith(compareBy<Todo, Long?>(nullsLast()) { it.reminderAt }.thenByDescending { it.priority.level })
            .take(MAX_ITEMS)
            .map { todo ->
                TodoWidgetItem(
                    id = todo.id,
                    title = todo.title,
                    reminderAt = todo.reminderAt,
                    link = DeepLinks.url(DeepLink.Tasks(todo.id)),
                )
            }
        return TodoWidgetState.Tasks(
            remainingCount = remaining.size,
            items = items,
            visibleUntil = expiresAt,
            link = DeepLinks.url(DeepLink.Tasks()),
        )
    }

    companion object {
        /** Le widget Android (liste défilante) en montre au plus 5, le widget iOS 3 ou 4 selon sa taille. */
        const val MAX_ITEMS = 5
    }
}
