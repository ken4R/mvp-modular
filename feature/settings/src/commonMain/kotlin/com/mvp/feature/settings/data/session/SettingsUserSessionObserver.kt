package com.mvp.feature.settings.data.session

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserAccount
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.feature.settings.data.source.ProfileLocalDataSource
import com.mvp.feature.settings.data.source.SettingsLocalDataSource
import com.mvp.feature.settings.domain.model.UserProfile
import com.mvp.feature.settings.domain.usecase.UpdateProfileUseCase

/** Les réglages suivent la session : profil pré-rempli à la connexion, tout effacé à la déconnexion. */
internal class SettingsUserSessionObserver(
    private val settings: SettingsLocalDataSource,
    private val profile: ProfileLocalDataSource,
) : UserSessionObserver {

    // Un profil déjà rempli (même compte qui se reconnecte) n'est pas écrasé.
    override suspend fun onLogin(account: UserAccount): AppResult<Unit> = safeCall {
        if (profile.current().displayName.isEmpty()) {
            profile.save(
                UserProfile(
                    displayName = account.displayName.take(UpdateProfileUseCase.MAX_NAME_LENGTH),
                    email = account.email,
                ),
            )
        }
    }

    override suspend fun clearUserData(): AppResult<Unit> = safeCall {
        profile.clear()
        settings.clear()
    }
}
