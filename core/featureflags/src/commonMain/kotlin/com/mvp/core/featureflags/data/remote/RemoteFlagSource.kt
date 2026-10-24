package com.mvp.core.featureflags.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

/** Source des flags distants : une autre implémentation (Firebase, backend) peut remplacer le JSON hébergé. */
interface RemoteFlagSource {
    /** Valeurs des flags livrés, par clé. Lève une exception si la réponse est absente ou refusée. */
    suspend fun fetch(): Map<String, Boolean>
}

/**
 * JSON publié sur GitHub Pages (`remote-config/flags.json`), lu avec le client Ktor partagé : aucun jeton, cookie ni
 * identifiant d'appareil n'est envoyé. Le corps est lu avec un plafond, jamais d'après `Content-Length`.
 */
internal class HostedJsonFlagSource(private val client: HttpClient, private val url: String) : RemoteFlagSource {

    init {
        require(url.startsWith("https://")) { "Les flags distants passent uniquement par HTTPS" }
    }

    override suspend fun fetch(): Map<String, Boolean> =
        client.prepareGet(url) {
            timeout { requestTimeoutMillis = TIMEOUT_MILLIS }
        }.execute { response ->
            // Un octet de plus que le maximum suffit pour savoir que la réponse est trop grande.
            val bytes = response.bodyAsChannel().readRemaining(RemoteFlagsParser.MAX_BYTES + 1L).readByteArray()
            RemoteFlagsParser.parse(bytes)
        }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
