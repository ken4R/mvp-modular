package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.analytics.AnalyticsEvent
import com.mvp.core.common.domain.analytics.AnalyticsTracker
import com.mvp.core.common.domain.applock.AppLockPreference
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.SessionExpiry
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Case cochée dans le widget Tâches (ADR 0026). Paramètre : id de la tâche.
 *
 * Mêmes règles que ce que le widget peut afficher (ADR 0011) : rien n'est modifié sans session valide
 * ([AppError.Unauthorized]) ni avec le verrou Face ID / empreinte activé ([AppError.PermissionDenied]), même si
 * un widget pas encore redessiné montre encore la tâche. La demande peut arriver en retard (iOS : à la prochaine
 * ouverture de l'app) ou deux fois : elle **termine** la tâche, sans jamais la rouvrir. Une tâche déjà terminée
 * réussit sans rien écrire ; une tâche supprimée entre-temps (corbeille comprise) échoue avec [AppError.NotFound].
 * La modification part au serveur par la file d'envoi, comme une case cochée dans l'app (ADR 0012).
 */
class CompleteTodoFromWidgetUseCase(
    private val repository: TodoRepository,
    private val session: SessionExpiry,
    private val appLock: AppLockPreference,
    private val analytics: AnalyticsTracker,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : UseCase<String, Unit>(dispatcher) {

    // La lecture de l'état et l'inversion se suivent sans transaction : deux taps rapides ne doivent pas les entrelacer.
    override suspend fun execute(params: String): AppResult<Unit> = mutex.withLock { complete(params) }

    private suspend fun complete(params: String): AppResult<Unit> {
        val expiresAt = session.observeExpiresAt().first()
        if (expiresAt == null || expiresAt <= now()) return AppResult.Failure(AppError.Unauthorized)
        if (appLock.observeAppLockEnabled().first()) return AppResult.Failure(AppError.PermissionDenied)
        val todo = repository.observeTodos().first().firstOrNull { it.id == params }
            ?: return AppResult.Failure(AppError.NotFound)
        if (todo.isDone) return AppResult.Success(Unit)
        return when (val toggled = repository.toggleTodo(todo.id)) {
            is AppResult.Failure -> toggled
            is AppResult.Success -> {
                analytics.track(AnalyticsEvent.TaskCompleted)
                AppResult.Success(Unit)
            }
        }
    }

    private companion object {
        /** Partagé par toutes les instances (le use case est une factory Koin). */
        val mutex = Mutex()
    }
}
