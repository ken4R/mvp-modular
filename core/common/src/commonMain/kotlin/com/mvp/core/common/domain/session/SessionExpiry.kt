package com.mvp.core.common.domain.session

import kotlinx.coroutines.flow.Flow

/**
 * Fin de la session en cours, fournie par la connexion (`feature:auth`) aux features qui programment
 * des actions dans le temps (rappels de tâches) : rien ne doit se déclencher hors d'une session valide.
 */
interface SessionExpiry {

    /** Fin de la session en epoch ms, ou `null` sans session valide (déconnecté ou expirée). */
    fun observeExpiresAt(): Flow<Long?>
}
