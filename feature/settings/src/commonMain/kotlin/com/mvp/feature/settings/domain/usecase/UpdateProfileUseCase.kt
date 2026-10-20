package com.mvp.feature.settings.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.settings.domain.model.UserProfile
import com.mvp.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Enregistre le profil. Règles métier : nom obligatoire (50 caractères max),
 * e-mail facultatif mais au bon format (254 caractères max) s'il est renseigné.
 */
class UpdateProfileUseCase(
    private val repository: SettingsRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<UserProfile, UserProfile>(dispatcher) {

    override suspend fun execute(params: UserProfile): AppResult<UserProfile> {
        val profile = UserProfile(displayName = params.displayName.trim(), email = params.email.trim())
        return when {
            profile.displayName.isEmpty() -> AppResult.Failure(AppError.Validation("Le nom est obligatoire"))
            profile.displayName.length > MAX_NAME_LENGTH ->
                AppResult.Failure(AppError.Validation("$MAX_NAME_LENGTH caractères maximum pour le nom"))
            profile.email.length > MAX_EMAIL_LENGTH ->
                AppResult.Failure(AppError.Validation("$MAX_EMAIL_LENGTH caractères maximum pour l'e-mail"))
            profile.email.isNotEmpty() && !EMAIL_REGEX.matches(profile.email) ->
                AppResult.Failure(AppError.Validation("Adresse e-mail invalide"))
            else -> repository.updateProfile(profile)
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 50
        const val MAX_EMAIL_LENGTH = 254
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
