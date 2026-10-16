package com.mvp.core.common.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn

/**
 * Base des cas d'usage qui observent une donnée dans le temps (cache, base locale, websocket...).
 */
abstract class FlowUseCase<in P, out R>(private val dispatcher: CoroutineDispatcher) {

    operator fun invoke(params: P): Flow<AppResult<R>> = execute(params)
        .catch { emit(AppResult.Failure(AppError.Unknown(it.message))) }
        .flowOn(dispatcher)

    protected abstract fun execute(params: P): Flow<AppResult<R>>
}
