package com.mvp.core.common.domain.notification

import kotlinx.coroutines.flow.Flow

/**
 * Contrat entre le réglage Notifications (`feature:settings`) et les features qui envoient des
 * notifications (rappels de `feature:todo`), sans que les features dépendent les unes des autres.
 * La feature qui possède le réglage déclare l'implémentation dans son module Koin.
 */
interface NotificationsPreference {

    /** `true` tant que l'utilisateur accepte les notifications de l'app (réglage et permission système). */
    fun observeNotificationsEnabled(): Flow<Boolean>
}
