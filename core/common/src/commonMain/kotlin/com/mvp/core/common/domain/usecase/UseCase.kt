package com.mvp.core.common.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Base de tous les cas d'usage (one-shot).
 * Chaque use case encapsule une seule règle métier et s'exécute sur le [dispatcher] fourni.
 */
abstract class UseCase<in P, out R>(private val dispatcher: CoroutineDispatcher) {

    @Throws(CancellationException::class)
    suspend operator fun invoke(params: P): AppResult<R> = withContext(dispatcher) {
        try {
            execute(params)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppResult.Failure(AppError.Unknown(e.message))
        }
    }

    protected abstract suspend fun execute(params: P): AppResult<R>
}

/** Paramètre à utiliser pour un use case sans entrée. */
data object NoParams
