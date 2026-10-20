package com.mvp.feature.auth.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserAccount
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.auth.domain.model.Credentials
import com.mvp.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Connecte l'utilisateur. Règles métier : e-mail obligatoire et valide (254 caractères max,
 * comparé sans tenir compte de la casse), mot de passe obligatoire (128 caractères max).
 * Si un autre compte était connecté sur l'appareil (session expirée sans déconnexion),
 * ses données sont effacées avant d'ouvrir la nouvelle session.
 * Les features sont ensuite prévenues ([UserSessionObserver.onLogin]), par exemple pour
 * pré-remplir le profil ; un échec à cette étape n'empêche pas la connexion.
 */
class LoginUseCase(
    private val repository: AuthRepository,
    private val observers: List<UserSessionObserver>,
    dispatcher: CoroutineDispatcher,
) : UseCase<Credentials, UserAccount>(dispatcher) {

    override suspend fun execute(params: Credentials): AppResult<UserAccount> {
        val credentials = Credentials(email = params.email.trim().lowercase(), password = params.password)
        validate(credentials)?.let { return AppResult.Failure(it) }

        val session = when (val result = repository.authenticate(credentials)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        val previous = when (val result = repository.lastAccount()) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        if (previous != null && previous.id != session.account.id) {
            observers.clearAllUserData().let { if (it is AppResult.Failure) return it }
        }
        repository.saveSession(session).let { if (it is AppResult.Failure) return it }

        observers.forEach { it.onLogin(session.account) }
        return AppResult.Success(session.account)
    }

    private fun validate(credentials: Credentials): AppError.Validation? = when {
        credentials.email.isEmpty() -> AppError.Validation("L'e-mail est obligatoire")
        credentials.email.length > MAX_EMAIL_LENGTH -> AppError.Validation("$MAX_EMAIL_LENGTH caractères maximum pour l'e-mail")
        !EMAIL_REGEX.matches(credentials.email) -> AppError.Validation("Adresse e-mail invalide")
        credentials.password.isEmpty() -> AppError.Validation("Le mot de passe est obligatoire")
        credentials.password.length > MAX_PASSWORD_LENGTH ->
            AppError.Validation("$MAX_PASSWORD_LENGTH caractères maximum pour le mot de passe")
        else -> null
    }

    companion object {
        const val MAX_EMAIL_LENGTH = 254
        const val MAX_PASSWORD_LENGTH = 128
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
