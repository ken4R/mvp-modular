package com.mvp.feature.auth.data.source

import com.mvp.core.common.domain.session.UserAccount
import com.mvp.feature.auth.domain.model.Credentials
import com.mvp.feature.auth.domain.model.Session
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Identifiants refusés par le service d'authentification. */
internal class InvalidCredentialsException : Exception("Identifiants refusés")

/**
 * Service d'authentification. Le prototype n'a pas encore de backend : [FakeAuthRemoteDataSource]
 * le simule sur l'appareil. Un vrai backend se branchera ici (appel HTTP via `core:network`)
 * sans rien changer au domaine ni aux apps.
 */
interface AuthRemoteDataSource {
    /** Renvoie une nouvelle session, ou lève [InvalidCredentialsException]. */
    suspend fun login(credentials: Credentials): Session
}

/**
 * Faux service de démonstration : accepte n'importe quel e-mail avec le mot de passe
 * [DEMO_PASSWORD]. Ce n'est pas une protection : il ne sert qu'à faire tourner le parcours
 * de connexion tant qu'il n'y a pas de backend.
 */
internal class FakeAuthRemoteDataSource(
    private val now: () -> Long = ::currentTimeMillis,
    private val latency: Duration = 600.milliseconds,
    private val sessionDuration: Duration = 7.days,
    private val tokenGenerator: () -> String = ::randomToken,
) : AuthRemoteDataSource {

    override suspend fun login(credentials: Credentials): Session {
        delay(latency)
        if (credentials.password != DEMO_PASSWORD) throw InvalidCredentialsException()
        val email = credentials.email
        return Session(
            // Même e-mail, même compte : identifiant stable d'une connexion à l'autre.
            account = UserAccount(id = "demo:$email", displayName = displayNameOf(email), email = email),
            token = tokenGenerator(),
            expiresAtEpochMillis = now() + sessionDuration.inWholeMilliseconds,
        )
    }

    /** « jean.dupont@exemple.fr » devient « Jean Dupont ». */
    private fun displayNameOf(email: String): String =
        email.substringBefore('@')
            .split('.', '_', '-', '+')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }
            .take(MAX_DISPLAY_NAME_LENGTH)
            .ifEmpty { email }

    companion object {
        const val DEMO_PASSWORD = "demo1234"
        private const val MAX_DISPLAY_NAME_LENGTH = 50
    }
}

@OptIn(ExperimentalTime::class)
internal fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

@OptIn(ExperimentalUuidApi::class)
private fun randomToken(): String = Uuid.random().toString()
