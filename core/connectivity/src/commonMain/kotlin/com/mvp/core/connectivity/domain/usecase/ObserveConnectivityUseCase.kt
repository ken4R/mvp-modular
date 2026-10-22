package com.mvp.core.connectivity.domain.usecase

import com.mvp.core.common.domain.connectivity.ConnectivityObserver
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * `true` tant que l'appareil a accès à internet : les écrans qui dépendent du réseau (Météo, Catalogue)
 * affichent un bandeau « Hors ligne » sinon. Une seule émission par changement.
 */
class ObserveConnectivityUseCase(
    private val connectivity: ConnectivityObserver,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, Boolean>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<Boolean>> =
        connectivity.observeIsOnline().distinctUntilChanged().map { AppResult.Success(it) }
}
