package com.mvp.core.network

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerializationException

/** [safeCall] pour un appel réseau : les exceptions Ktor deviennent des [AppError]. */
suspend fun <T> safeApiCall(block: suspend () -> T): AppResult<T> =
    safeCall(mapError = ::networkError, block = block)

internal fun networkError(error: Throwable): AppError = when {
    error is ResponseException -> when (error.response.status) {
        HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> AppError.Unauthorized
        HttpStatusCode.NotFound -> AppError.NotFound
        else -> AppError.Unknown("Erreur serveur (${error.response.status.value})")
    }
    // JSON illisible (Ktor enveloppe la SerializationException dans sa propre exception).
    error is SerializationException || error.cause is SerializationException ->
        AppError.Unknown("Réponse inattendue du serveur")
    // Pas de connexion, délai dépassé, DNS... : tout le reste est un problème de réseau.
    else -> AppError.Network
}
