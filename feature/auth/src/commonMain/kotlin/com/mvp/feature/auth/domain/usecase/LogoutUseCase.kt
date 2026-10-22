package com.mvp.feature.auth.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Déconnecte l'utilisateur : efface d'abord ses données locales dans toutes les features
 * (tâches, réglages, profil), puis la session. Si l'effacement échoue, la session est gardée,
 * pour que l'utilisateur puisse réessayer et qu'aucune donnée ne reste au suivant. Les features
 * sont ensuite prévenues de la déconnexion ([UserSessionObserver.onLogout]).
 */
class LogoutUseCase(
    private val repository: AuthRepository,
    private val observers: List<UserSessionObserver>,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Unit>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Unit> {
        observers.clearAllUserData().let { if (it is AppResult.Failure) return it }
        return repository.clearSession().also { result ->
            if (result is AppResult.Success) observers.forEach { it.onLogout() }
        }
    }
}
