package com.mvp.feature.auth.data.repository

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserAccount
import com.mvp.feature.auth.data.source.AuthRemoteDataSource
import com.mvp.feature.auth.data.source.AuthServiceUnavailableException
import com.mvp.feature.auth.data.source.DEMO_ACCOUNT_PREFIX
import com.mvp.feature.auth.data.source.InvalidCredentialsException
import com.mvp.feature.auth.data.source.SessionLocalDataSource
import com.mvp.feature.auth.data.source.currentTimeMillis
import com.mvp.feature.auth.domain.model.Credentials
import com.mvp.feature.auth.domain.model.Session
import com.mvp.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class AuthRepositoryImpl(
    private val remote: AuthRemoteDataSource,
    private val local: SessionLocalDataSource,
    private val now: () -> Long = ::currentTimeMillis,
    // Faux dans les builds distribués : une session du faux service restée sur l'appareil n'y ouvre rien.
    private val acceptsDemoSessions: Boolean = true,
) : AuthRepository {

    // L'expiration est vérifiée à chaque émission (au lancement, à la connexion, à la déconnexion).
    override fun observeSession(): Flow<Session?> =
        local.observe().map { session -> session?.takeIf { it.expiresAtEpochMillis > now() && isAccepted(it) } }

    private fun isAccepted(session: Session): Boolean =
        acceptsDemoSessions || !session.account.id.startsWith(DEMO_ACCOUNT_PREFIX)

    override suspend fun lastAccount(): AppResult<UserAccount?> = safeCall { local.current()?.account }

    override suspend fun authenticate(credentials: Credentials): AppResult<Session> =
        safeCall(
            mapError = {
                when (it) {
                    is InvalidCredentialsException -> AppError.Unauthorized
                    is AuthServiceUnavailableException -> AppError.Unavailable
                    else -> AppError.Unknown()
                }
            },
        ) {
            remote.login(credentials)
        }

    override suspend fun saveSession(session: Session): AppResult<Unit> = safeCall { local.save(session) }

    override suspend fun clearSession(): AppResult<Unit> = safeCall { local.clear() }
}
