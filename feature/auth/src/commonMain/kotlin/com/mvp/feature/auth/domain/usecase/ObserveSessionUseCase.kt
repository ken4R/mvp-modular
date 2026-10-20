package com.mvp.feature.auth.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserAccount
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Compte connecté, ou `null` s'il faut afficher l'écran de connexion. Le jeton n'en sort pas. */
class ObserveSessionUseCase(
    private val repository: AuthRepository,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, UserAccount?>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<UserAccount?>> =
        repository.observeSession().map { it?.account }.distinctUntilChanged().map { AppResult.Success(it) }
}
