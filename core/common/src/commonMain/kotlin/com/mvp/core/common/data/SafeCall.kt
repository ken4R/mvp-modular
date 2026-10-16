package com.mvp.core.common.data

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import kotlin.coroutines.cancellation.CancellationException

/**
 * À utiliser dans les implémentations de repository pour convertir
 * les exceptions techniques (réseau, base...) en [AppError].
 */
suspend fun <T> safeCall(
    mapError: (Throwable) -> AppError = { AppError.Unknown(it.message) },
    block: suspend () -> T,
): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    AppResult.Failure(mapError(e))
}
