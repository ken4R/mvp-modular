package com.mvp.feature.settings.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.settings.domain.repository.AppLockRepository
import kotlinx.coroutines.CoroutineDispatcher

/** À appeler uniquement après une authentification système réussie (Face ID, empreinte ou code). */
class UnlockAppUseCase(
    private val repository: AppLockRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Unit>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Unit> = AppResult.Success(repository.unlock())
}
