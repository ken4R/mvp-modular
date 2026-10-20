package com.mvp.feature.auth.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserSessionObserver

/**
 * Efface les données de l'utilisateur dans toutes les features. Chaque feature est tentée même
 * si une autre échoue ; le premier échec est renvoyé.
 */
internal suspend fun List<UserSessionObserver>.clearAllUserData(): AppResult<Unit> =
    map { it.clearUserData() }.firstOrNull { it is AppResult.Failure } ?: AppResult.Success(Unit)
