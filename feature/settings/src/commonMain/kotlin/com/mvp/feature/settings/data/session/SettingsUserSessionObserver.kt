package com.mvp.feature.settings.data.session

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserAccount
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.feature.settings.data.source.ProfileLocalDataSource
import com.mvp.feature.settings.data.source.SettingsLocalDataSource
import com.mvp.feature.settings.domain.model.UserProfile
import com.mvp.feature.settings.domain.model.UserSettings
import com.mvp.feature.settings.domain.usecase.UpdateProfileUseCase

/**
 * Les réglages suivent la session : profil pré-rempli à la connexion, tout effacé à la déconnexion, sauf
 * l'apparence (thème, accent) : préférence de l'appareil sans donnée personnelle, gardée pour l'écran de
 * connexion et le compte suivant (ADR 0015).
 */
internal class SettingsUserSessionObserver(
    private val settings: SettingsLocalDataSource,
    private val profile: ProfileLocalDataSource,
) : UserSessionObserver {

    // Un profil déjà rempli (même compte qui se reconnecte) n'est pas écrasé.
    override suspend fun onLogin(account: UserAccount): AppResult<Unit> = safeCall {
        if (profile.current().displayName.isEmpty()) {
            // Données effacées (autre compte) : le verrou gardé par clearUserData ne vaut pas pour ce compte.
            settings.update { it.copy(appLockEnabled = false) }
            profile.save(
                UserProfile(
                    displayName = account.displayName.take(UpdateProfileUseCase.MAX_NAME_LENGTH),
                    email = account.email,
                ),
            )
        }
    }

    /**
     * Tout est remis à zéro sauf le verrou Face ID / empreinte, en une seule écriture : pendant la déconnexion,
     * la session est encore valide et les autres features ne sont pas forcément effacées (ordre non garanti,
     * échec possible). Le verrou reste donc actif jusqu'à la fin de la déconnexion ([onLogout]) : ni l'app
     * ni le widget Tâches ne montrent les tâches sans authentification entre-temps (ADR 0011).
     */
    override suspend fun clearUserData(): AppResult<Unit> = safeCall {
        profile.clear()
        settings.update { UserSettings.initial().copy(appLockEnabled = it.appLockEnabled, appearance = it.appearance) }
    }

    /** Session effacée : plus rien à protéger, la base des réglages est vidée, sauf l'apparence. */
    override suspend fun onLogout() {
        settings.clearExceptAppearance()
    }
}
