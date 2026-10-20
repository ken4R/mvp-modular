package com.mvp.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Client HTTP partagé par toutes les features : JSON tolérant aux champs inconnus,
 * délais d'attente, et statuts HTTP non 2xx convertis en exception (puis en [com.mvp.core.common.domain.error.AppError]
 * par [safeApiCall]).
 *
 * [engine] permet d'injecter un MockEngine en test ; sinon le moteur de la plateforme
 * est utilisé (HttpURLConnection sur Android, Darwin sur iOS).
 */
fun createHttpClient(engine: HttpClientEngine? = null): HttpClient {
    val config: HttpClientConfig<*>.() -> Unit = {
        expectSuccess = true
        install(ContentNegotiation) { json(NetworkJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 15_000
        }
    }
    return if (engine == null) HttpClient(config) else HttpClient(engine, config)
}

internal val NetworkJson = Json { ignoreUnknownKeys = true }
