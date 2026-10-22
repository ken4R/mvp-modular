package com.mvp.core.common.domain.applock

import kotlinx.coroutines.flow.Flow

/**
 * Contrat entre le réglage Sécurité (`feature:settings`) et les features qui affichent des données hors de
 * l'app (widget de l'écran d'accueil), sans que les features dépendent les unes des autres : verrou activé,
 * rien de l'utilisateur ne doit être visible sans passer par Face ID / empreinte. Voir docs/adr/0011.
 */
interface AppLockPreference {

    /** `true` tant que le déverrouillage Face ID / empreinte est activé dans Réglages, même app déverrouillée. */
    fun observeAppLockEnabled(): Flow<Boolean>
}
