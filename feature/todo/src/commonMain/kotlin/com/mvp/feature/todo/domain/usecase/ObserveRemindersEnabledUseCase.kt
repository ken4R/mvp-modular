package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.notification.NotificationsPreference
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** `true` si les rappels peuvent partir (réglage Notifications activé) ; sinon l'écran le signale. */
class ObserveRemindersEnabledUseCase(
    private val notifications: NotificationsPreference,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, Boolean>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<Boolean>> =
        notifications.observeNotificationsEnabled().map { AppResult.Success(it) }
}
