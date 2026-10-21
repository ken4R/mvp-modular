package com.mvp.feature.settings.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * État du verrou de l'app (Face ID / Touch ID / empreinte). Le verrou est une barrière d'interface :
 * la vérification biométrique est faite par le système (LocalAuthentication / BiometricPrompt),
 * l'app ne fait que masquer son contenu tant qu'elle n'a pas réussi. Voir docs/adr/0007.
 */
interface AppLockRepository {
    /** `true` tant que le contenu de l'app doit rester masqué derrière l'écran de déverrouillage. */
    fun observeLocked(): Flow<Boolean>

    /** L'app passe en arrière-plan : il faudra se ré-authentifier au retour. */
    fun lock()

    /** L'authentification système vient de réussir. */
    fun unlock()
}
